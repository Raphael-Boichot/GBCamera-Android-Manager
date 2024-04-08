package com.mraulio.gbcameramanager.ui.palettes;

import static com.mraulio.gbcameramanager.MainActivity.lastSeenGalleryImage;
import static com.mraulio.gbcameramanager.ui.gallery.GalleryUtils.frameChange;
import static com.mraulio.gbcameramanager.utils.Utils.gbcPalettesList;
import static com.mraulio.gbcameramanager.utils.Utils.hashFrames;
import static com.mraulio.gbcameramanager.utils.Utils.showNotification;
import static com.mraulio.gbcameramanager.utils.Utils.sortPalettes;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.OnColorSelectedListener;
import com.flask.colorpicker.builder.ColorPickerClickListener;
import com.flask.colorpicker.builder.ColorPickerDialogBuilder;
import com.mraulio.gbcameramanager.MainActivity;
import com.mraulio.gbcameramanager.model.GbcImage;
import com.mraulio.gbcameramanager.ui.gallery.UpdateImageAsyncTask;
import com.mraulio.gbcameramanager.utils.Utils;
import com.mraulio.gbcameramanager.db.PaletteDao;
import com.mraulio.gbcameramanager.R;
import com.mraulio.gbcameramanager.gameboycameralib.codecs.ImageCodec;
import com.mraulio.gbcameramanager.model.GbcPalette;
import com.mraulio.gbcameramanager.ui.gallery.GalleryFragment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import java.util.Locale;

//Using this color picker:https://github.com/QuadFlask/colorpicker
//Another color picker: https://github.com/yukuku/ambilwarna
public class PalettesFragment extends Fragment {

    CustomGridViewAdapterPalette paletteAdapter;
    GridView gridViewPalettes;
    ImageView iv1, iv2, iv3, iv4;
    int lastPicked = Color.rgb(155, 188, 15);
    EditText et1, et2, et3, et4;
    String placeholderString = "";
    String newPaletteId = "";
    String newPaletteName = "";
    int[] palette;
    final String PALETTE_ID_REGEX = "^[a-z0-9]{2,}$";

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_palettes, container, false);
        MainActivity.pressBack = false;
        MainActivity.currentFragment = MainActivity.CURRENT_FRAGMENT.PALETTES;

        Button btnAdd = view.findViewById(R.id.btnAdd);
        Button btnExportPaletteJson = view.findViewById(R.id.btnExportPaletteJson);
        gridViewPalettes = view.findViewById(R.id.gridViewPalettes);

        CustomGridViewAdapterPalette customGridViewAdapterPalette = new CustomGridViewAdapterPalette(getContext(), R.layout.palette_grid_item, Utils.gbcPalettesList, true, false, true);

        gridViewPalettes.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            private int clickCount = 0;
            private final Handler handler = new Handler();
            private int palettePos = 0;

            private final Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    //Single tap action
                    //Ask to create a new palette using this as a base, or edit this palette name/colors
                    clickCount = 0;
                    palette = Utils.gbcPalettesList.get(palettePos).getPaletteColorsInt().clone();//Clone so it doesn't overwrite base palette colors.
                    newPaletteId = Utils.gbcPalettesList.get(palettePos).getPaletteId();
                    newPaletteName = Utils.gbcPalettesList.get(palettePos).getPaletteName();
                    paletteDialog(palette, newPaletteId, newPaletteName);
                }
            };

            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                clickCount++;
                if (clickCount == 1) {
                    // Start timer to detect the double tap
                    palettePos = position;
                    handler.postDelayed(runnable, 300);
                } else if (clickCount == 2) {
                    clickCount = 0;
                    // Stop timer and make double tap action
                    handler.removeCallbacks(runnable);
                    if (lastPicked == position) {
                        GbcPalette pal = Utils.gbcPalettesList.get(palettePos);
                        pal.setFavorite(pal.isFavorite() ? false : true);
                        paletteAdapter.notifyDataSetChanged();
                        new UpdatePaletteAsyncTask(pal).execute();
                        sortPalettes();
                    }
                }
                lastPicked = position;
            }
        });

        gridViewPalettes.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                GbcPalette selectedPalette = gbcPalettesList.get(position);

                if (selectedPalette.getPaletteId().equals("bw")) {
                    Utils.toast(getContext(), getString(R.string.cant_delete_base_palette));
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle(getString(R.string.delete_dialog_palette) + Utils.gbcPalettesList.get(position).getPaletteId() + "?");
                    builder.setMessage(getString(R.string.sure_dialog_palette));

                    // Crear un ImageView y establecer la imagen deseada
                    ImageView imageView = new ImageView(getContext());
                    imageView.setAdjustViewBounds(true);
                    imageView.setPadding(30, 10, 30, 10);
                    imageView.setImageBitmap(Utils.gbcPalettesList.get(position).paletteViewer());

                    // Agregar el ImageView al diseño del diálogo
                    builder.setView(imageView);

                    builder.setPositiveButton(getString(R.string.delete), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            new SavePaletteAsyncTask(Utils.gbcPalettesList.get(position), false).execute();
                            String paletteToDelete = Utils.gbcPalettesList.get(position).getPaletteId();
                            Utils.gbcPalettesList.remove(position);
                            sortPalettes();
                            //If an image has the deleted palette in image or frame the palette is set to to default bw
                            //Also need to change the bitmap on the completeImageList so it changes on the Gallery
                            for (int i = 0; i < Utils.gbcImagesList.size(); i++) {
                                if (Utils.gbcImagesList.get(i).getPaletteId().equals(paletteToDelete) || Utils.gbcImagesList.get(i).getFramePaletteId().equals(paletteToDelete)) {
                                    Utils.gbcImagesList.get(i).setPaletteId("bw");
                                    Utils.gbcImagesList.get(i).setFramePaletteId("bw");
                                    Utils.gbcImagesList.get(i).setInvertPalette(false);
                                    Utils.gbcImagesList.get(i).setInvertFramePalette(false);

                                    //If the bitmap cache already has the bitmap, change it.
                                    if (GalleryFragment.diskCache.get(Utils.gbcImagesList.get(i).getHashCode()) != null) {
                                        GbcImage gbcImage = Utils.gbcImagesList.get(i);
                                        try {
                                            Bitmap imageBitmap = frameChange(gbcImage, gbcImage.getFrameId(), gbcImage.isInvertPalette(), gbcImage.isInvertFramePalette(), gbcImage.isLockFrame(), false);
                                            GalleryFragment.diskCache.put(Utils.gbcImagesList.get(i).getHashCode(), imageBitmap);
                                            Utils.imageBitmapCache.put(Utils.gbcImagesList.get(i).getHashCode(), imageBitmap);
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    new UpdateImageAsyncTask(Utils.gbcImagesList.get(i)).execute();
                                }

                            }
                            paletteAdapter.notifyDataSetChanged();
                        }
                    });
                    builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            //No action
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.show();
                }
                return true;//true so the normal onItemClick doesn't show
            }
        });

        btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                palette = Utils.gbcPalettesList.get(0).getPaletteColorsInt().clone();//Clone so it doesn't overwrite base palette colors.
                paletteDialog(palette, "", "");
            }
        });

        btnExportPaletteJson.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    PaletteJsonCreator();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        paletteAdapter = customGridViewAdapterPalette;
        gridViewPalettes.setAdapter(paletteAdapter);
        return view;
    }

    private class SavePaletteAsyncTask extends AsyncTask<Void, Void, Void> {

        //To add the new palette as a parameter
        private final GbcPalette gbcPalette;
        private final boolean save;

        public SavePaletteAsyncTask(GbcPalette gbcPalette, boolean save) {
            this.gbcPalette = gbcPalette;
            this.save = save;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            PaletteDao paletteDao = MainActivity.db.paletteDao();
            if (save) {
                paletteDao.insert(gbcPalette);
            } else {
                paletteDao.delete(gbcPalette);
            }
            return null;
        }
    }

    private void PaletteJsonCreator() throws JSONException {
        JSONObject json = new JSONObject();
        JSONObject stateObj = new JSONObject();
        JSONArray palettesArr = new JSONArray();
        for (GbcPalette palette : Utils.gbcPalettesList) {
            JSONObject paletteObj = new JSONObject();
            paletteObj.put("shortName", palette.getPaletteId());
            paletteObj.put("name", palette.getPaletteName());
            paletteObj.put("favorite", palette.isFavorite());

            JSONArray paletteArr = new JSONArray();
            for (int color : palette.getPaletteColorsInt()) {
                String hexColor = "#" + Integer.toHexString(color).substring(2);
                paletteArr.put(hexColor);
            }
            paletteObj.put("palette", paletteArr);
            paletteObj.put("origin", "GbCamera Android Manager");
            palettesArr.put(paletteObj);
        }
        stateObj.put("palettes", palettesArr);
        json.put("state", stateObj);

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
        String fileName = "palettes_" + dateFormat.format(new Date()) + ".json";
        File file = new File(Utils.PALETTES_FOLDER, fileName);

        try (FileWriter fileWriter = new FileWriter(file)) {
            fileWriter.write(json.toString(2));
            Utils.toast(getContext(), getString(R.string.toast_palettes_json));
            showNotification(getContext(), file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void paletteDialog(int[] palette, String paletteId, String paletteName) {
        final Dialog dialog = new Dialog(getContext());
        dialog.setContentView(R.layout.palette_creator);
        final boolean[] validId = {false};

        ImageView ivPalette = dialog.findViewById(R.id.ivPalette);
        Button btnSavePalette = dialog.findViewById(R.id.btnSavePalette);
        btnSavePalette.setEnabled(false);

        EditText etPaletteId = dialog.findViewById(R.id.etPaletteId);
        EditText etPaletteName = dialog.findViewById(R.id.etPaletteName);
        et1 = dialog.findViewById(R.id.et1);
        et2 = dialog.findViewById(R.id.et2);
        et3 = dialog.findViewById(R.id.et3);
        et4 = dialog.findViewById(R.id.et4);
        etPaletteId.setImeOptions(EditorInfo.IME_ACTION_DONE);//WHen pressing enter
        et1.setImeOptions(EditorInfo.IME_ACTION_DONE);
        et2.setImeOptions(EditorInfo.IME_ACTION_DONE);
        et3.setImeOptions(EditorInfo.IME_ACTION_DONE);
        et4.setImeOptions(EditorInfo.IME_ACTION_DONE);

        etPaletteId.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    placeholderString = etPaletteId.getText().toString();

                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(etPaletteId.getWindowToken(), 0);

                    return true;
                }
                return false;
            }
        });

        etPaletteId.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                placeholderString = etPaletteId.getText().toString();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                placeholderString = etPaletteId.getText().toString();

                if (!placeholderString.equals("")) {

                    //Check if the ID is valid
                    if (placeholderString.matches(PALETTE_ID_REGEX)) {
                        validId[0] = true;
                        btnSavePalette.setEnabled(validId[0]);

                        etPaletteId.setError(null);

                        for (GbcPalette palette : Utils.gbcPalettesList) {
                            if (palette.getPaletteId().equals(placeholderString)) {
                                etPaletteId.setError(getString(R.string.toast_palettes_error));
                                validId[0] = false;
                                btnSavePalette.setEnabled(validId[0]);
                                break;
                            } else {
                                validId[0] = true;
                                btnSavePalette.setEnabled(validId[0]);
                                etPaletteId.setError(null);
                            }
                        }
                    } else {
                        etPaletteId.setError(getString(R.string.et_palette_id_error));
                        validId[0] = false;
                        btnSavePalette.setEnabled(validId[0]);
                    }

                } else {
                    etPaletteId.setHint(getString(R.string.et_palette_id));
                    validId[0] = false;
                    btnSavePalette.setEnabled(validId[0]);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        etPaletteName.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    placeholderString = etPaletteId.getText().toString();

                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(etPaletteId.getWindowToken(), 0);

                    return true;
                }
                return false;
            }
        });

        etPaletteId.setText(paletteId);
        etPaletteName.setText(paletteName);

        et1.setText("#" + Integer.toHexString(palette[0]).substring(2).toUpperCase());
        et2.setText("#" + Integer.toHexString(palette[1]).substring(2).toUpperCase());
        et3.setText("#" + Integer.toHexString(palette[2]).substring(2).toUpperCase());
        et4.setText("#" + Integer.toHexString(palette[3]).substring(2).toUpperCase());

        et1.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    placeholderString = et1.getText().toString();
                    // El usuario ha confirmado la escritura.
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(etPaletteId.getWindowToken(), 0);

                    return true;
                }
                return false;
            }
        });
        et1.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                placeholderString = et1.getText().toString();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    iv1.setBackgroundColor(parseColor(et1.getText().toString()));
                    palette[0] = parseColor(et1.getText().toString());
                    ivPalette.setImageBitmap(paletteMaker(palette));

                } catch (NumberFormatException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Este método se llama después de que el texto cambie.
            }
        });
        et2.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Este método se llama antes de que el texto cambie.
                placeholderString = et2.getText().toString();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Este método se llama cuando el texto cambia.
                try {
                    iv2.setBackgroundColor(parseColor(et2.getText().toString()));
                    palette[1] = parseColor(et2.getText().toString());
                    ivPalette.setImageBitmap(paletteMaker(palette));

                } catch (NumberFormatException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Este método se llama después de que el texto cambie.
            }
        });
        et3.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Este método se llama antes de que el texto cambie.
                placeholderString = et3.getText().toString();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Este método se llama cuando el texto cambia.
                try {
                    iv3.setBackgroundColor(parseColor(et3.getText().toString()));
                    palette[2] = parseColor(et3.getText().toString());
                    ivPalette.setImageBitmap(paletteMaker(palette));

                } catch (NumberFormatException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        et4.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                placeholderString = et4.getText().toString();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    iv4.setBackgroundColor(parseColor(et4.getText().toString()));
                    palette[3] = parseColor(et4.getText().toString());
                    ivPalette.setImageBitmap(paletteMaker(palette));

                } catch (NumberFormatException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Este método se llama después de que el texto cambie.
            }
        });
        try {
            ivPalette.setImageBitmap(paletteMaker(palette));
        } catch (IOException e) {
            e.printStackTrace();
        }

        iv1 = dialog.findViewById(R.id.iv1);
        iv2 = dialog.findViewById(R.id.iv2);
        iv3 = dialog.findViewById(R.id.iv3);
        iv4 = dialog.findViewById(R.id.iv4);
        iv1.setBackgroundColor(palette[0]);
        iv1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ColorPickerDialogBuilder
                        .with(getContext())
                        .setTitle(getString(R.string.choose_color))
                        .initialColor(lastPicked)
                        .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
                        .density(12)
                        .showAlphaSlider(false)
                        .setOnColorSelectedListener(new OnColorSelectedListener() {
                            @Override
                            public void onColorSelected(int selectedColor) {
                                Utils.toast(getContext(), getString(R.string.selected_color) + Integer.toHexString(selectedColor).substring(2).toUpperCase());
                            }
                        })
                        .setPositiveButton("OK", new ColorPickerClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int selectedColor, Integer[] allColors) {
                                iv1.setBackgroundColor(selectedColor);
                                palette[0] = selectedColor;
                                try {
                                    ivPalette.setImageBitmap(paletteMaker(palette));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                lastPicked = selectedColor;
                                et1.setText("#" + Integer.toHexString(palette[0]).substring(2).toUpperCase());
                            }
                        })
                        .setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .build()
                        .show();
            }
        });
        iv2.setBackgroundColor(palette[1]);
        iv2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ColorPickerDialogBuilder
                        .with(getContext())
                        .setTitle(getString(R.string.choose_color))
                        .initialColor(lastPicked)
                        .wheelType(ColorPickerView.WHEEL_TYPE.CIRCLE)
                        .density(12)
                        .showAlphaSlider(false)
                        .setOnColorSelectedListener(new OnColorSelectedListener() {
                            @Override
                            public void onColorSelected(int selectedColor) {
                                Utils.toast(getContext(), getString(R.string.selected_color) + Integer.toHexString(selectedColor).substring(2).toUpperCase());
                            }
                        })
                        .setPositiveButton("ok", new ColorPickerClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int selectedColor, Integer[] allColors) {
                                iv2.setBackgroundColor(selectedColor);
                                palette[1] = selectedColor;
                                try {
                                    ivPalette.setImageBitmap(paletteMaker(palette));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                lastPicked = selectedColor;
                                et2.setText("#" + Integer.toHexString(palette[1]).substring(2).toUpperCase());

                            }
                        })
                        .setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .build()
                        .show();
            }
        });

        iv3.setBackgroundColor(palette[2]);
        iv3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ColorPickerDialogBuilder
                        .with(getContext())
                        .setTitle(getString(R.string.choose_color))
                        .initialColor(lastPicked)
                        .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
                        .density(12)
                        .showAlphaSlider(false)
                        .setOnColorSelectedListener(new OnColorSelectedListener() {
                            @Override
                            public void onColorSelected(int selectedColor) {
                                Utils.toast(getContext(), getString(R.string.selected_color) + Integer.toHexString(selectedColor).substring(2).toUpperCase());
                            }
                        })
                        .setPositiveButton("OK", new ColorPickerClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int selectedColor, Integer[] allColors) {
                                iv3.setBackgroundColor(selectedColor);
                                palette[2] = selectedColor;
                                try {
                                    ivPalette.setImageBitmap(paletteMaker(palette));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                lastPicked = selectedColor;
                                et3.setText("#" + Integer.toHexString(palette[2]).substring(2).toUpperCase());

                            }
                        })
                        .setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .build()
                        .show();
            }
        });

        iv4.setBackgroundColor(palette[3]);
        iv4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ColorPickerDialogBuilder
                        .with(getContext())
                        .setTitle(getString(R.string.choose_color))
                        .initialColor(lastPicked)
                        .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
                        .density(12)
                        .showAlphaSlider(false)
                        .setOnColorSelectedListener(new OnColorSelectedListener() {
                            @Override
                            public void onColorSelected(int selectedColor) {
                                Utils.toast(getContext(), getString(R.string.selected_color) + Integer.toHexString(selectedColor).substring(2).toUpperCase());
                            }
                        })
                        .setPositiveButton("ok", new ColorPickerClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int selectedColor, Integer[] allColors) {
                                iv4.setBackgroundColor(selectedColor);
                                palette[3] = selectedColor;
                                try {
                                    ivPalette.setImageBitmap(paletteMaker(palette));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                lastPicked = selectedColor;
                                et4.setText("#" + Integer.toHexString(palette[3]).substring(2).toUpperCase());

                            }
                        })
                        .setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .build()
                        .show();
            }
        });

        btnSavePalette.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean alreadyExistingPaletteId = false;
                newPaletteId = etPaletteId.getText().toString().trim();
                newPaletteName = etPaletteName.getText().toString().trim();

                if (!newPaletteId.equals("")) {
                    for (GbcPalette paleta : Utils.gbcPalettesList) {
                        if (paleta.getPaletteId().equals(newPaletteId)) {
                            alreadyExistingPaletteId = true;
                            Utils.toast(getContext(), getString(R.string.toast_palettes_error));
                            break;
                        }
                    }
                    if (!alreadyExistingPaletteId) {
                        GbcPalette newPalette = new GbcPalette();

                        //CHANGE THIS WITH A REGEX, ALL LOWERCASE, MIN 2 CHARS, ONLY LETTERS AND NUMBERS
                        newPalette.setPaletteId(newPaletteId);
                        newPalette.setPaletteName(newPaletteName);

                        newPalette.setPaletteColors(palette);
                        Utils.gbcPalettesList.add(newPalette);
                        Utils.hashPalettes.put(newPalette.getPaletteId(), newPalette);
                        gridViewPalettes.setAdapter(paletteAdapter);
                        Utils.toast(getContext(), getString(R.string.palette_added));
                        dialog.hide();
                        //To add it to the database
                        new SavePaletteAsyncTask(newPalette, true).execute();//Adding the new palette to the database

                    }
                }
            }
        });

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;
        int desiredWidth = (int) (screenWidth * 0.8);
        Window window = dialog.getWindow();
        window.setLayout(desiredWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.show();
    }

    private Bitmap paletteMaker(int[] palette) throws IOException {
        ImageCodec imageCodec = new ImageCodec(160, 144);//imageBytes.length/40 to get the height of the image
        Bitmap bitmap;
        Bitmap upscaledBitmap;
        byte[] imageBytes;
        if (Utils.gbcImagesList.size() == 0 || (Utils.gbcImagesList.get(0).getImageBytes().length / 40 != 144)) {//If there are no images, or they are not 144 height
            imageBytes = Utils.encodeImage(hashFrames.get("gbcam03").getFrameBitmap(), "bw");
            bitmap = imageCodec.decodeWithPalette(palette, imageBytes, false);
            upscaledBitmap = Bitmap.createScaledBitmap(bitmap, Utils.framesList.get(0).getFrameBitmap().getWidth() * 6, Utils.framesList.get(0).getFrameBitmap().getHeight() * 6, false);
        } else {
            GbcImage gbcImage = Utils.gbcImagesList.get(lastSeenGalleryImage);
            bitmap = frameChange(gbcImage, gbcImage.getFrameId(), gbcImage.isInvertPalette(), gbcImage.isInvertFramePalette(), gbcImage.isLockFrame(), false);
            imageBytes = Utils.encodeImage(bitmap, "bw");
            bitmap = imageCodec.decodeWithPalette(palette, imageBytes, false);
            upscaledBitmap = Bitmap.createScaledBitmap(bitmap, 160 * 6, 144 * 6, false);
        }

        return upscaledBitmap;
    }

    public static int parseColor(String colorString) {
        if (colorString.charAt(0) != '#') {
            colorString = "#" + colorString;
        }
        return Color.parseColor(colorString);
    }
}