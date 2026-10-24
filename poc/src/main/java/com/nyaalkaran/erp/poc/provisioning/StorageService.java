package com.nyaalkaran.erp.poc.provisioning;

import com.nyaalkaran.erp.poc.common.PocProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.stereotype.Service;

/** One private bucket per company per purchased module (CD-01), created at provisioning, never lazily. */
@Service
public class StorageService {

    private final PocProperties props;
    private final MinioClient client;

    public StorageService(PocProperties props) {
        this.props = props;
        this.client = props.storage().enabled()
                ? MinioClient.builder()
                    .endpoint(props.storage().endpoint())
                    .credentials(props.storage().accessKey(), props.storage().secretKey())
                    .build()
                : null;
    }

    public boolean enabled() {
        return props.storage().enabled();
    }

    /** Idempotent: "create if missing, never create-or-fail" (master §7.3). New MinIO buckets are private. */
    public void ensureBucket(String bucket) throws Exception {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    public boolean bucketExists(String bucket) throws Exception {
        return client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
    }
}
