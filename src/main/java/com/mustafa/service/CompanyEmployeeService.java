package com.mustafa.service;

import com.mustafa.dto.request.HireEmployeeRequest;
import com.mustafa.dto.request.SalaryPaymentRequest;
import com.mustafa.dto.request.UpdateEmployeeRequest;
import com.mustafa.dto.response.CompanyEmployeeResponse;
import com.mustafa.dto.response.TransactionResponse;

import java.util.List;

public interface CompanyEmployeeService {

    CompanyEmployeeResponse hireEmployee(String managerIdentityNumber, HireEmployeeRequest request);

    List<CompanyEmployeeResponse> getMyEmployees(String managerIdentityNumber);

    CompanyEmployeeResponse updateEmployee(String managerIdentityNumber, String employeeIdentityNumber, UpdateEmployeeRequest request);

    void removeEmployee(String managerIdentityNumber, String employeeIdentityNumber);

    List<TransactionResponse> paySalaries(String managerIdentityNumber, SalaryPaymentRequest request);
}