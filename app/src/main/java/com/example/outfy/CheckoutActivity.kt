package com.example.outfy

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.outfy.adapter.CheckoutItemAdapter
import com.example.outfy.databinding.ActivityCheckoutBinding
import com.example.outfy.model.UserAddress
import com.example.outfy.model.cityLine
import com.example.outfy.model.isComplete
import com.example.outfy.model.streetLine
import com.example.outfy.viewmodel.CheckoutViewModel
import java.text.NumberFormat
import java.util.Locale

class CheckoutActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCheckoutBinding
    private val viewModel: CheckoutViewModel by viewModels()
    private lateinit var checkoutItemAdapter: CheckoutItemAdapter
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(0, statusBarHeight, 0, 0)
            insets
        }

        setupRecyclerView()
        setupActions()
        observeViewModel()
        viewModel.loadCheckout()
    }

    private fun setupRecyclerView() {
        checkoutItemAdapter = CheckoutItemAdapter()
        binding.rvOrderSummary.apply {
            layoutManager = LinearLayoutManager(this@CheckoutActivity)
            adapter = checkoutItemAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupActions() {
        binding.btnBack.setOnClickListener { finish() }
        binding.tvAddressAction.setOnClickListener {
            val hasSavedAddress = viewModel.address.value?.isComplete() == true
            val showingForm = viewModel.showAddressForm.value == true
            viewModel.showAddressEditor(if (hasSavedAddress) !showingForm else true)
        }
        binding.btnPlaceOrder.setOnClickListener {
            val address = if (binding.addressFormCard.isVisible) {
                readAddressFromForm()
            } else {
                viewModel.address.value ?: readAddressFromForm()
            }
            viewModel.placeOrder(address)
        }
    }

    private fun observeViewModel() {
        viewModel.cartItems.observe(this) { items ->
            checkoutItemAdapter.submitList(items)
            binding.tvOrderSummaryMeta.text = resources.getQuantityString(
                R.plurals.checkout_items_count,
                items.size,
                items.size
            )
            binding.emptyState.isVisible = items.isEmpty()
            binding.checkoutContent.isVisible = items.isNotEmpty()
        }

        viewModel.address.observe(this) { address ->
            populateAddressForm(address)
            renderAddress(address, viewModel.showAddressForm.value == true)
        }

        viewModel.showAddressForm.observe(this) { showForm ->
            renderAddress(viewModel.address.value ?: UserAddress(), showForm)
        }

        viewModel.subtotal.observe(this) { subtotal ->
            binding.tvSubtotal.text = currencyFormat.format(subtotal)
        }

        viewModel.deliveryCharge.observe(this) { deliveryCharge ->
            binding.tvShipping.text =
                if (deliveryCharge == 0.0) getString(R.string.free_shipping)
                else currencyFormat.format(deliveryCharge)
        }

        viewModel.tax.observe(this) { tax ->
            binding.tvTax.text = currencyFormat.format(tax)
        }

        viewModel.total.observe(this) { total ->
            val totalText = currencyFormat.format(total)
            binding.tvTotal.text = totalText
            binding.btnPlaceOrder.text = getString(R.string.place_order_with_total, totalText)
        }

        viewModel.canPlaceOrder.observe(this) { canPlace ->
            binding.btnPlaceOrder.isEnabled = canPlace && viewModel.isPlacingOrder.value != true
        }

        viewModel.isPlacingOrder.observe(this) { isPlacing ->
            binding.progressBar.isVisible = isPlacing || viewModel.isLoading.value == true
            binding.btnPlaceOrder.isEnabled = !isPlacing && viewModel.canPlaceOrder.value == true
            if (isPlacing) {
                binding.btnPlaceOrder.text = getString(R.string.placing_order)
            } else {
                val totalText = currencyFormat.format(viewModel.total.value ?: 0.0)
                binding.btnPlaceOrder.text = getString(R.string.place_order_with_total, totalText)
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.isVisible = isLoading || viewModel.isPlacingOrder.value == true
        }

        viewModel.message.observe(this) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }

        viewModel.orderPlacedId.observe(this) { orderId ->
            if (!orderId.isNullOrBlank()) {
                startActivity(Intent(this, OrdersActivity::class.java))
                viewModel.clearOrderPlacedEvent()
                finish()
            }
        }
    }

    private fun renderAddress(address: UserAddress, showForm: Boolean) {
        val hasSavedAddress = address.isComplete()
        binding.savedAddressCard.isVisible = hasSavedAddress && !showForm
        binding.addressFormCard.isVisible = showForm || !hasSavedAddress

        binding.tvSavedName.text = address.fullName
        binding.tvSavedLabel.text = address.label.ifBlank { "HOME" }
        binding.tvSavedStreet.text = address.streetLine()
        binding.tvSavedCity.text = address.cityLine()
        binding.tvSavedPhone.text = address.phone

        binding.tvAddressAction.text = when {
            hasSavedAddress && !showForm -> getString(R.string.change_text)
            hasSavedAddress -> getString(R.string.use_saved_address)
            else -> getString(R.string.add_address_action)
        }
    }

    private fun populateAddressForm(address: UserAddress) {
        if (binding.etFullName.text?.toString().isNullOrBlank()) {
            binding.etFullName.setText(address.fullName)
        }
        if (binding.etPhone.text?.toString().isNullOrBlank()) {
            binding.etPhone.setText(address.phone)
        }
        if (binding.etLine1.text?.toString().isNullOrBlank()) {
            binding.etLine1.setText(address.line1)
        }
        if (binding.etLine2.text?.toString().isNullOrBlank()) {
            binding.etLine2.setText(address.line2)
        }
        if (binding.etCity.text?.toString().isNullOrBlank()) {
            binding.etCity.setText(address.city)
        }
        if (binding.etState.text?.toString().isNullOrBlank()) {
            binding.etState.setText(address.state)
        }
        if (binding.etPincode.text?.toString().isNullOrBlank()) {
            binding.etPincode.setText(address.pincode)
        }

        when (address.label.uppercase()) {
            "WORK" -> binding.chipWork.isChecked = true
            else -> binding.chipHome.isChecked = true
        }
    }

    private fun readAddressFromForm(): UserAddress {
        return UserAddress(
            fullName = binding.etFullName.text?.toString().orEmpty().trim(),
            phone = binding.etPhone.text?.toString().orEmpty().trim(),
            line1 = binding.etLine1.text?.toString().orEmpty().trim(),
            line2 = binding.etLine2.text?.toString().orEmpty().trim(),
            city = binding.etCity.text?.toString().orEmpty().trim(),
            state = binding.etState.text?.toString().orEmpty().trim(),
            pincode = binding.etPincode.text?.toString().orEmpty().trim(),
            label = if (binding.chipWork.isChecked) "WORK" else "HOME"
        )
    }
}
