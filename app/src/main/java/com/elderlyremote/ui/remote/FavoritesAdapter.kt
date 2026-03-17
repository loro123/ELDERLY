package com.elderlyremote.ui.remote

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView
import com.elderlyremote.databinding.ItemFavoriteBinding
import com.elderlyremote.model.FavoriteConfig
import java.io.File

class FavoritesAdapter(
    private val context: Context,
    private var items: List<FavoriteConfig>,
    private val imagesDir: File,
    private val onFavoriteClick: (FavoriteConfig) -> Unit
) : RecyclerView.Adapter<FavoritesAdapter.FavoriteViewHolder>() {

    inner class FavoriteViewHolder(val binding: ItemFavoriteBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val binding = ItemFavoriteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FavoriteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        val fav = items[position]
        val btn = holder.binding.btnFavorite

        btn.text = fav.label

        // Load optional image as compound drawable
        if (fav.imageFile != null) {
            val imgFile = File(imagesDir, fav.imageFile!!)
            if (imgFile.exists()) {
                try {
                    val bmp = BitmapFactory.decodeFile(imgFile.absolutePath)
                    val size = context.resources.getDimensionPixelSize(
                        com.elderlyremote.R.dimen.favorite_icon_size
                    )
                    val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, size, size, true)
                    val drawable = BitmapDrawable(context.resources, scaled)
                    btn.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null)
                    btn.compoundDrawablePadding = 8
                } catch (e: Exception) {
                    btn.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
                }
            } else {
                btn.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
            }
        } else {
            btn.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
        }

        btn.setOnClickListener {
            animateButtonPress(btn)
            onFavoriteClick(fav)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<FavoriteConfig>) {
        items = newItems
        notifyDataSetChanged()
    }

    /** Brief visual highlight (~150ms) on button press per spec. */
    private fun animateButtonPress(btn: Button) {
        btn.isPressed = true
        btn.postDelayed({ btn.isPressed = false }, 150)
    }
}
