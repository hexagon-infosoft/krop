package com.avito.android.krop.demo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.annotation.ColorInt
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.view.ViewTreeObserver
import android.widget.*
import com.avito.android.krop.KropView
import com.squareup.picasso.Picasso
import me.priyesh.chroma.ChromaDialog
import me.priyesh.chroma.ColorMode
import me.priyesh.chroma.ColorSelectListener
import java.io.InputStream


class MainActivity : AppCompatActivity() {

    private lateinit var navigation: BottomNavigationView
    private lateinit var viewFlipper: ViewFlipper
    private lateinit var cropContainer: ViewFlipper
    private lateinit var kropView: KropView
    private lateinit var resultImage: ImageView
    private lateinit var inputOffset: SeekBar
    private lateinit var inputAspectX: SeekBar
    private lateinit var inputAspectY: SeekBar
    private lateinit var pickColorButton: Button
    private lateinit var inputOverlayColor: EditText
    private lateinit var overlayShape: RadioGroup

    private var uri: Uri = Uri.EMPTY

    private var target: KropTarget? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        tintStatusBarIcons(this, true)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        navigation = findViewById(R.id.navigation)
        viewFlipper = findViewById(R.id.view_flipper)
        cropContainer = findViewById(R.id.crop_container)
        resultImage = findViewById(R.id.result_image)

        inputOffset = findViewById(R.id.input_offset)
        inputAspectX = findViewById(R.id.input_aspect_x)
        inputAspectY = findViewById(R.id.input_aspect_y)
        pickColorButton = findViewById(R.id.pick_color_button)
        inputOverlayColor = findViewById(R.id.input_overlay_color)
        overlayShape = findViewById(R.id.overlay_shape)

        kropView = findViewById(R.id.krop_view)

        pickColorButton.setOnClickListener {
            ChromaDialog.Builder()
                    .initialColor(Color.parseColor(inputOverlayColor.text.toString()))
                    .colorMode(ColorMode.ARGB)
                    .onColorSelected(listener = object : ColorSelectListener {
                        override fun onColorSelected(color: Int) {
                            setInputOverlayColor(color)
                        }
                    })
                    .create()
                    .show(supportFragmentManager, getString(R.string.select_color))
        }

        navigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.action_crop -> showCrop()
                R.id.action_settings -> showSettings()
                R.id.action_result -> showPreview()
            }
            true
        }

        uri = savedInstanceState?.getParcelable(KEY_URI) ?: Uri.EMPTY

        if (savedInstanceState == null) {
            inputAspectX.progress = 0
            inputAspectY.progress = 0

            setInputOverlayColor(resources.getColor(R.color.default_overlay_color))

            kropView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    inputOffset.progress = ((kropView.width / 2) / convertPixelsToDp(
                            px = resources.getDimension(R.dimen.default_offset),
                            context = kropView.context
                    )).toInt()
                    kropView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
            })
        } else {
            navigation.post {
                when (navigation.selectedItemId) {
                    R.id.action_crop -> showCrop()
                    R.id.action_settings -> showSettings()
                    R.id.action_result -> showPreview()
                }
            }
        }

        if (uri == Uri.EMPTY) {
            target = KropTarget(cropContainer, kropView, false)
            Picasso
                    .with(this)
                    .load("file:///android_asset/default_image.jpg")
                    .error(R.drawable.image)
                    .centerInside()
                    .resize(1024, 1024)
                    .config(Bitmap.Config.RGB_565)
                    .into(target)
        } else {
            loadUri(resetZoom = false)
        }
    }

    fun getBitmapFromAsset(context: Context, filePath: String): Bitmap {
        val assetManager = context.assets
        var input: InputStream? = null
        val bitmap: Bitmap
        try {
            input = assetManager.open(filePath)
            bitmap = BitmapFactory.decodeStream(input)
        } finally {
            input?.close()
        }
        return bitmap
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState?.putParcelable(KEY_URI, uri)
    }

    private fun setInputOverlayColor(color: Int) {
        val hexColor = colorToHex(color)
        inputOverlayColor.setText(hexColor)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        val menuRes = when (navigation.selectedItemId) {
            R.id.action_crop -> R.menu.main_crop
            R.id.action_settings -> R.menu.menu_settings
            else -> R.menu.main
        }
        inflater.inflate(menuRes, menu)
        for (c in 0 until menu.size()) {
            val item = menu.getItem(c)
            var drawable = item.icon
            drawable = DrawableCompat.wrap(drawable)
            DrawableCompat.setTint(drawable, ContextCompat.getColor(this, R.color.color_accent))
            item.icon = drawable
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_select -> {
                selectPicture()
                return true
            }
            R.id.menu_apply -> {
                applySettings()
                showCrop()
                navigation.selectedItemId = R.id.action_crop
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun selectPicture() {
        val intent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_picture)), REQUEST_PICK_IMAGE)
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_PICK_IMAGE) {
            if (resultCode == Activity.RESULT_OK) {
                uri = data?.data ?: return
                loadUri(resetZoom = true)
            }
        }
    }

    private fun loadUri(resetZoom: Boolean) {
        target = KropTarget(cropContainer, kropView, resetZoom)
        Picasso
                .with(this)
                .load(uri)
                .centerInside()
                .error(R.drawable.image)
                .resize(1024, 1024)
                .config(Bitmap.Config.RGB_565)
                .noFade()
                .into(target)
    }

    private fun applySettings() {
        try {
            val offset = (inputOffset.progress * (kropView.width / 2)) / 100
            val aspectX = inputAspectX.progress + 1
            val aspectY = inputAspectY.progress + 1
            val overlayColor = Color.parseColor(inputOverlayColor.text.toString())
            val shape = when(overlayShape.checkedRadioButtonId) {
                R.id.shape_oval -> 0
                else -> 1
            }
            kropView.apply {
                applyAspectRatio(aspectX, aspectY)
                applyOffset(offset)
                applyOverlayColor(overlayColor)
                applyOverlayShape(shape)
            }
        } catch (ignored: Throwable) {
            Snackbar.make(kropView, R.string.unable_to_apply_settings, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun showCrop() {
        viewFlipper.displayedChild = 0
        invalidateOptionsMenu()
    }

    private fun showSettings() {
        viewFlipper.displayedChild = 1
        invalidateOptionsMenu()
    }

    private fun showPreview() {
        val bitmap = kropView.getCroppedBitmap()
        resultImage.setImageBitmap(bitmap)
        viewFlipper.displayedChild = 2
        invalidateOptionsMenu()
    }

    private fun colorToHex(@ColorInt colorInt: Int) = "#" + Integer.toHexString(colorInt)

}

private const val REQUEST_PICK_IMAGE: Int = 42
private const val KEY_URI = "key_uri"