package com.jobstar.backend.service;

import java.io.IOException;
import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class SupabaseResumeFileStorage implements ResumeFileStorage {

    private final String endpoint;
    private final String region;
    private final String accessKey;
    private final String secretKey;
    private final String bucket;
    private S3Client client;

    public SupabaseResumeFileStorage(
            @Value("${app.storage.supabase.endpoint:}") String endpoint,
            @Value("${app.storage.supabase.region:}") String region,
            @Value("${app.storage.supabase.access-key:}") String accessKey,
            @Value("${app.storage.supabase.secret-key:}") String secretKey,
            @Value("${app.storage.supabase.bucket:resumes}") String bucket) {
        this.endpoint = endpoint;
        this.region = region;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucket = bucket;
    }

    @Override
    public void upload(String storageKey, MultipartFile file) {
        try {
            storageClient().putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(storageKey)
                    .contentType(file.getContentType())
                    .build(), RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read the uploaded resume.", exception);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not store the resume file.", exception);
        }
    }

    @Override
    public StoredResumeFile download(String storageKey) {
        try {
            ResponseBytes<GetObjectResponse> object = storageClient().getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(storageKey)
                    .build(), ResponseTransformer.toBytes());
            return new StoredResumeFile(object.asByteArray(), object.response().contentType(), null);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not retrieve the resume file.", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            storageClient().deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(storageKey).build());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not remove the resume file.", exception);
        }
    }

    private S3Client storageClient() {
        if (endpoint.isBlank() || region.isBlank() || accessKey.isBlank() || secretKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Resume file storage has not been configured yet.");
        }
        if (client == null) {
            client = S3Client.builder()
                    .endpointOverride(URI.create(endpoint))
                    .region(Region.of(region))
                    .forcePathStyle(true)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                    .build();
        }
        return client;
    }
}
