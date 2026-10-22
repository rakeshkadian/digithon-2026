package com.example.employeemanagement.dto;

import com.example.employeemanagement.entity.Employee;
import java.time.LocalDate;

public record EmployeeResponse(Long id, String firstName, String lastName, String email,
                               String phone, String department, LocalDate joiningDate) {
    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(employee.getId(), employee.getFirstName(), employee.getLastName(),
                employee.getEmail(), employee.getPhone(), employee.getDepartment(), employee.getJoiningDate());
    }
}
