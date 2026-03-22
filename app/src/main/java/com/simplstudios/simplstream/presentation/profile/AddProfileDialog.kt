package com.simplstudios.simplstream.presentation.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.simplstudios.simplstream.R
import com.simplstudios.simplstream.domain.model.Profile

/**
 * Dialog for creating a new profile
 */
class AddProfileDialog(
    private val onProfileCreated: (name: String, avatarIndex: Int, pin: String?, isKids: Boolean) -> Unit,
    private val onVerifyParentalPin: ((pin: String, onSuccess: () -> Unit, onError: () -> Unit) -> Unit)? = null,
    private val onSetParentalPin: ((pin: String) -> Unit)? = null,
    private val hasParentalPin: ((callback: (Boolean) -> Unit) -> Unit)? = null
) : DialogFragment() {
    
    private var selectedAvatarIndex = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.SimplStreamDialogTheme)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_add_profile, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            val nameInput = view.findViewById<EditText>(R.id.profile_name_input)
            val pinInput = view.findViewById<EditText>(R.id.pin_input)
            val kidsCheckbox = view.findViewById<CheckBox>(R.id.kids_checkbox)
            val avatarRecycler = view.findViewById<RecyclerView>(R.id.avatar_recycler)
            val createButton = view.findViewById<Button>(R.id.create_button)
            val cancelButton = view.findViewById<Button>(R.id.cancel_button)
            val errorText = view.findViewById<TextView>(R.id.error_text)
            
            // Setup avatar selection
            avatarRecycler.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            val avatarAdapter = AvatarAdapter { index ->
                selectedAvatarIndex = index
            }
            avatarRecycler.adapter = avatarAdapter
            
            // Validation
            nameInput.doAfterTextChanged {
                val name = it?.toString() ?: ""
                createButton.isEnabled = name.length >= 2
                errorText.visibility = View.GONE
            }
            
            // Create button
            createButton.setOnClickListener {
                val name = nameInput.text.toString().trim()
                val pin = pinInput.text.toString().takeIf { it.length == 4 }
                val isKids = kidsCheckbox.isChecked

                if (name.length < 2) {
                    errorText.text = "Name must be at least 2 characters"
                    errorText.visibility = View.VISIBLE
                    return@setOnClickListener
                }

                if (pinInput.text.isNotEmpty() && pinInput.text.length != 4) {
                    errorText.text = "PIN must be 4 digits"
                    errorText.visibility = View.VISIBLE
                    return@setOnClickListener
                }

                if (isKids && hasParentalPin != null) {
                    // Kids profile requires parental PIN verification/setup
                    hasParentalPin.invoke { exists ->
                        if (exists) {
                            // Verify existing parental PIN
                            showParentalPinInput(errorText) {
                                onProfileCreated(name, selectedAvatarIndex, pin, true)
                                dismiss()
                            }
                        } else {
                            // Need to set parental PIN first
                            showSetParentalPinInput(errorText) {
                                onProfileCreated(name, selectedAvatarIndex, pin, true)
                                dismiss()
                            }
                        }
                    }
                } else {
                    onProfileCreated(name, selectedAvatarIndex, pin, isKids)
                    dismiss()
                }
            }
            
            // Cancel button
            cancelButton.setOnClickListener {
                dismiss()
            }
            
            // Focus for TV
            nameInput.requestFocus()
        } catch (e: Exception) {
            e.printStackTrace()
            dismiss()
        }
    }
    
    private fun showParentalPinInput(errorText: TextView, onVerified: () -> Unit) {
        val pinDialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_pin_verify_delete, null)
        val pinInput = pinDialogView.findViewById<EditText>(R.id.pin_input)
        val pinError = pinDialogView.findViewById<TextView>(R.id.error_text)

        val pinDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.SimplStreamDialogTheme)
            .setTitle("Parental PIN Required")
            .setMessage("Enter the parental PIN to create a Kids profile")
            .setView(pinDialogView)
            .create()

        pinDialogView.findViewById<View>(R.id.cancel_button).setOnClickListener {
            pinDialog.dismiss()
        }

        pinDialogView.findViewById<View>(R.id.verify_button).setOnClickListener {
            val entered = pinInput.text.toString()
            if (entered.length != 4) {
                pinError.text = "PIN must be 4 digits"
                pinError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            onVerifyParentalPin?.invoke(entered, {
                pinDialog.dismiss()
                onVerified()
            }, {
                pinError.text = "Incorrect parental PIN"
                pinError.visibility = View.VISIBLE
                pinInput.text?.clear()
            })
        }

        pinDialog.show()
        pinInput.requestFocus()
    }

    private fun showSetParentalPinInput(errorText: TextView, onPinSet: () -> Unit) {
        val pinDialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_parental_pin, null)
        val titleText = pinDialogView.findViewById<TextView>(R.id.dialog_title)
        val subtitleText = pinDialogView.findViewById<TextView>(R.id.dialog_subtitle)
        val currentPinContainer = pinDialogView.findViewById<View>(R.id.current_pin_container)
        val newPinInput = pinDialogView.findViewById<EditText>(R.id.new_pin_input)
        val confirmPinInput = pinDialogView.findViewById<EditText>(R.id.confirm_pin_input)
        val pinError = pinDialogView.findViewById<TextView>(R.id.error_text)

        titleText.text = "Set Parental PIN"
        subtitleText.text = "A parental PIN is required to create Kids profiles. This PIN controls parental settings."
        currentPinContainer.visibility = View.GONE

        val pinDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.SimplStreamDialogTheme)
            .setView(pinDialogView)
            .create()

        pinDialogView.findViewById<View>(R.id.cancel_button).setOnClickListener {
            pinDialog.dismiss()
        }

        pinDialogView.findViewById<View>(R.id.save_button).setOnClickListener {
            val newPin = newPinInput.text.toString()
            val confirmPin = confirmPinInput.text.toString()

            if (newPin.length != 4) {
                pinError.text = "PIN must be 4 digits"
                pinError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            if (newPin != confirmPin) {
                pinError.text = "PINs do not match"
                pinError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            onSetParentalPin?.invoke(newPin)
            pinDialog.dismiss()
            onPinSet()
        }

        pinDialog.show()
        newPinInput.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        // Set dialog size - fixed width for TV screens
        dialog?.window?.apply {
            // Convert 580dp to pixels for a proper width
            val widthDp = 580
            val widthPx = (widthDp * resources.displayMetrics.density).toInt()

            setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(android.view.Gravity.CENTER)
        }
    }
}

/**
 * Avatar selection adapter
 */
class AvatarAdapter(
    private val onAvatarSelected: (Int) -> Unit
) : RecyclerView.Adapter<AvatarAdapter.AvatarViewHolder>() {
    
    private var selectedIndex = 0
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AvatarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_avatar, parent, false)
        return AvatarViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: AvatarViewHolder, position: Int) {
        holder.bind(position, position == selectedIndex)
    }
    
    override fun getItemCount(): Int = Profile.AVATAR_COLORS.size
    
    inner class AvatarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatarView: View = itemView.findViewById(R.id.avatar_color)
        private val checkView: View = itemView.findViewById(R.id.avatar_check)
        
        fun bind(index: Int, isSelected: Boolean) {
            val color = Profile.AVATAR_COLORS[index]
            
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(color)
            }
            avatarView.background = drawable
            
            checkView.visibility = if (isSelected) View.VISIBLE else View.GONE
            
            itemView.setOnClickListener {
                val oldIndex = selectedIndex
                selectedIndex = index
                notifyItemChanged(oldIndex)
                notifyItemChanged(index)
                onAvatarSelected(index)
            }
            
            // Focus handling
            itemView.isFocusable = true
            itemView.setOnFocusChangeListener { _, hasFocus ->
                val scale = if (hasFocus) 1.04f else 1.0f
                itemView.animate().alpha(if (hasFocus) 1f else 0.7f).setDuration(120).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
            }
        }
    }
}
