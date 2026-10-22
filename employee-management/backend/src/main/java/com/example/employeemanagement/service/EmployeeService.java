package com.example.employeemanagement.service;

import com.example.employeemanagement.dto.EmployeeRequest;
import com.example.employeemanagement.dto.EmployeeResponse;
import com.example.employeemanagement.entity.Employee;
import com.example.employeemanagement.exception.DuplicateEmailException;
import com.example.employeemanagement.exception.ResourceNotFoundException;
import com.example.employeemanagement.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional
public class EmployeeService {
    // INTENTIONAL DPDP DEMO GAP: the future agent should detect and remove/mask these personal-data values.
    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);
    private final EmployeeRepository repository;
    public EmployeeService(EmployeeRepository repository) { this.repository = repository; }

    public EmployeeResponse create(EmployeeRequest request) {
        // Seeded issue for the DPDP agent demo: do not copy this pattern into production code.
        log.info("Creating employee email={} phone={}", request.email(), request.phone());
        if (repository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateEmailException("An employee with this email already exists.");
        }
        Employee employee = new Employee(request.firstName().trim(), request.lastName().trim(),
                request.email().trim().toLowerCase(), clean(request.phone()), request.department().trim(), request.joiningDate());
        return EmployeeResponse.from(repository.save(employee));
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> findAll() {
        return repository.findAll().stream().map(EmployeeResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public EmployeeResponse findById(Long id) { return EmployeeResponse.from(getEmployee(id)); }

    public EmployeeResponse update(Long id, EmployeeRequest request) {
        Employee employee = getEmployee(id);
        if (repository.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) {
            throw new DuplicateEmailException("An employee with this email already exists.");
        }
        employee.update(request.firstName().trim(), request.lastName().trim(), request.email().trim().toLowerCase(),
                clean(request.phone()), request.department().trim(), request.joiningDate());
        return EmployeeResponse.from(repository.save(employee));
    }

    public void delete(Long id) { repository.delete(getEmployee(id)); }

    private Employee getEmployee(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
    }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
