package com.elderlyremote.ui.admin

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.elderlyremote.R
import com.elderlyremote.databinding.FragmentFavoritesConfigBinding
import com.elderlyremote.databinding.ItemFavoriteConfigBinding
import com.elderlyremote.model.FavoriteConfig
import com.elderlyremote.model.FavoriteType
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FavoritesConfigFragment : Fragment() {

    private var _binding: FragmentFavoritesConfigBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminViewModel by activityViewModels()

    private var pendingImageFavId: Int = -1
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        val cfg = viewModel.config.value ?: return@registerForActivityResult
        val fav = cfg.favorites.firstOrNull { it.id == pendingImageFavId } ?: return@registerForActivityResult
        viewModel.importImageForFavorite(pendingImageFavId, uri, fav.label)
    }

    private lateinit var favAdapter: FavConfigAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCountButtons()
        setupRecyclerView()
        observeConfig()
    }

    private fun setupCountButtons() {
        binding.btnCount4.setOnClickListener { viewModel.setFavoritesCount(4) }
        binding.btnCount6.setOnClickListener { viewModel.setFavoritesCount(6) }
        binding.btnCount8.setOnClickListener { viewModel.setFavoritesCount(8) }
        binding.btnCount12.setOnClickListener { viewModel.setFavoritesCount(12) }
    }

    private fun setupRecyclerView() {
        favAdapter = FavConfigAdapter(
            onLabelChanged = { fav -> viewModel.updateFavorite(fav) },
            onTypeChanged = { fav -> viewModel.updateFavorite(fav) },
            onValueChanged = { fav -> viewModel.updateFavorite(fav) },
            onPickImage = { favId ->
                pendingImageFavId = favId
                imagePickerLauncher.launch("image/*")
            }
        )
        binding.rvFavorites.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFavorites.adapter = favAdapter

        // Drag-and-drop reorder
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                viewModel.reorderFavorites(vh.adapterPosition, target.adapterPosition)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled() = false
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
            }
        })
        touchHelper.attachToRecyclerView(binding.rvFavorites)
        favAdapter.touchHelper = touchHelper
    }

    private fun observeConfig() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.config.collectLatest { config ->
                config ?: return@collectLatest
                favAdapter.submitList(config.favorites.take(config.favoritesCount).toList())
                // Highlight active count button
                listOf(
                    4 to binding.btnCount4,
                    6 to binding.btnCount6,
                    8 to binding.btnCount8,
                    12 to binding.btnCount12
                ).forEach { (count, btn) ->
                    btn.isSelected = count == config.favoritesCount
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ---- Inner adapter ----

class FavConfigAdapter(
    private val onLabelChanged: (FavoriteConfig) -> Unit,
    private val onTypeChanged: (FavoriteConfig) -> Unit,
    private val onValueChanged: (FavoriteConfig) -> Unit,
    private val onPickImage: (Int) -> Unit
) : RecyclerView.Adapter<FavConfigAdapter.VH>() {

    var touchHelper: ItemTouchHelper? = null
    private var items: List<FavoriteConfig> = emptyList()

    fun submitList(list: List<FavoriteConfig>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(val binding: ItemFavoriteConfigBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemFavoriteConfigBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val fav = items[position]
        val b = holder.binding

        // Drag handle
        b.dragHandle.setOnTouchListener { _, _ ->
            touchHelper?.startDrag(holder)
            false
        }

        // Label
        b.etFavLabel.setText(fav.label)
        b.etFavLabel.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val updated = fav.copy(label = b.etFavLabel.text.toString())
                onLabelChanged(updated)
            }
        }

        // Type spinner
        val typeLabels = arrayOf("Channel #", "Single Key", "Macro")
        val spinnerAdapter = ArrayAdapter(b.root.context, android.R.layout.simple_spinner_item, typeLabels)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        b.spinnerFavType.adapter = spinnerAdapter
        b.spinnerFavType.setSelection(fav.type.ordinal)
        b.spinnerFavType.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val newType = FavoriteType.values()[pos]
                if (newType != fav.type) onTypeChanged(fav.copy(type = newType))
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Value field (digits for Type A, keycode hex for Type B)
        b.etFavValue.setText(when (fav.type) {
            FavoriteType.DIGIT_SEQUENCE -> fav.digits
            FavoriteType.SINGLE_KEY -> "0x%02X".format(fav.singleKeyCode.toInt() and 0xFF)
            FavoriteType.MACRO -> "(macro)"
        })
        b.etFavValue.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && fav.type == FavoriteType.DIGIT_SEQUENCE) {
                onValueChanged(fav.copy(digits = b.etFavValue.text.toString()))
            }
        }

        // Pick image
        b.btnPickImage.setOnClickListener { onPickImage(fav.id) }
    }

    override fun getItemCount() = items.size
}
