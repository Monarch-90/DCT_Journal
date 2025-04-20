package com.dct_journal.presentation.view

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dct_journal.databinding.ActivityRegistrationBinding // Сгенерированный ViewBinding
import com.dct_journal.presentation.view_model.RegistrationViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel // Koin для ViewModel

class RegistrationActivity : AppCompatActivity() {

    private var _binding: ActivityRegistrationBinding? = null
    private val binding get() = _binding!!

    // Получаем ViewModel через Koin
    private val viewModel: RegistrationViewModel by viewModel()

    private val tag = "RegistrationActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Инициализация ViewBinding
        _binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(tag, "Activity создана")

        setupViews()
        observeViewModel()
    }

    private fun setupViews() {
        binding.buttonRegister.setOnClickListener {
            val orderNumber = binding.editTextOrderNumber.text.toString().trim()
            Log.d(tag, "Нажата кнопка регистрации, номер: '$orderNumber'")
            // Вызываем метод ViewModel для регистрации
            viewModel.registerDevice(orderNumber)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            // repeatOnLifecycle гарантирует, что подписка активна только когда Activity видна
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Log.d(tag, "Обновление UI State: $state")
                    // Обновляем текстовое поле с Android ID
                    binding.textViewAndroidIdValue.text = state.androidId

                    // Показываем/скрываем ProgressBar
                    binding.progressBarRegistration.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    binding.buttonRegister.isEnabled = !state.isLoading // Блокируем кнопку во время загрузки

                    // Показываем результат регистрации (если он есть)
                    state.registrationResult?.let { result ->
                        // Показываем сообщение о результате (можно использовать Snackbar или Toast)
                        val message = result.message ?: if (result.success) "Успешно!" else "Ошибка!"
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                        Log.d(tag, "Показан результат регистрации: $message")

                         if (result.success) {
                             finish() // Закрыть Activity после успешной регистрации
                         }
                    }

                    // Показываем одноразовое событие ошибки (если есть)
                    state.errorEvent?.let { errorMessage ->
                        Toast.makeText(this@RegistrationActivity, errorMessage, Toast.LENGTH_LONG).show()
                        Log.w(tag, "Показано сообщение об ошибке: $errorMessage")
                        viewModel.errorEventHandled() // Сообщаем ViewModel, что событие обработано
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null // Очищаем binding для предотвращения утечек памяти
        Log.d(tag, "Activity уничтожена")
    }
}