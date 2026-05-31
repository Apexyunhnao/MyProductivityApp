package com.example.myproductivityapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myproductivityapp.data.AppDatabase
import com.example.myproductivityapp.data.model.Employee
import com.example.myproductivityapp.data.repository.EmployeeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EmployeeViewModel(application: Application) : AndroidViewModel(application) {

    private val client = com.example.myproductivityapp.MainActivity.cloudClient
    private val repository = EmployeeRepository(
        dao = AppDatabase.getDatabase(application).employeeDao(),
        client = client
    )

    private val _employees = MutableStateFlow<List<Employee>>(emptyList())
    val employees: StateFlow<List<Employee>> = _employees.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAll().collect { employeeList ->
                _employees.value = employeeList
            }
        }
    }

    fun addEmployee(name: String, phoneNumber: String) {
        viewModelScope.launch {
            val employee = Employee(
                name = name,
                employeeId = "",
                phoneNumber = phoneNumber
            )
            repository.save(employee)
        }
    }

    fun updateEmployee(employee: Employee) {
        viewModelScope.launch {
            repository.save(employee)
        }
    }

    fun deleteEmployee(employee: Employee) {
        viewModelScope.launch {
            repository.delete(employee)
        }
    }
}
