package com.nyaalkaran.erp.poc.provisioning;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
public class ProvisioningController {

    private final ProvisioningService provisioningService;

    public record JobView(ProvisioningJob job, List<ProvisioningStep> steps) {
    }

    @PostMapping("/companies/{companyCode}/provision")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobView provision(@PathVariable String companyCode) {
        ProvisioningJob job = provisioningService.requestCreateCompany(companyCode);
        return new JobView(job, provisioningService.steps(job.getId()));
    }

    @GetMapping("/companies/{companyCode}/provisioning-jobs")
    public List<JobView> jobs(@PathVariable String companyCode) {
        return provisioningService.jobs(companyCode).stream()
                .map(j -> new JobView(j, provisioningService.steps(j.getId())))
                .toList();
    }

    @PostMapping("/provisioning-jobs/{jobId}/retry")
    public JobView retry(@PathVariable Long jobId) {
        ProvisioningJob job = provisioningService.retry(jobId);
        return new JobView(job, provisioningService.steps(jobId));
    }
}
