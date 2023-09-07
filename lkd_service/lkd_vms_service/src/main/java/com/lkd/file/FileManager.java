package com.lkd.file;

import com.lkd.config.MinIOConfig;
import com.lkd.exception.LogicException;
import io.minio.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Component
public class FileManager {
    @Autowired
    private MinIOConfig minIOConfig;

    /**
     * 上传文件到MinIO
     * @param file
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws InvalidKeyException
     */
    public String uploadFile(MultipartFile file) {
        try {
            //创建客户端对象
            MinioClient minioClient = buildMinioClient();
            //创建添加图片的对象
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .object(file.getOriginalFilename())//文件名称
                    .contentType(file.getContentType())//文件类型
                    .stream(file.getInputStream(),file.getSize(),-1)  // partSize -1表示整体(不分片)上传
                    .bucket(minIOConfig.getBucket())//添加空间
                    .build();
            minioClient.putObject(putObjectArgs);//执行上传
            //构建返回文件的完整路径
            StringBuilder sbPhotoPath = new StringBuilder(minIOConfig.getReadPath());
            sbPhotoPath.append("/").append(minIOConfig.getBucket()).append("/").append(file.getOriginalFilename());
            return sbPhotoPath.toString();
        }catch (Exception ex){
            log.error("minio put file error.",ex);
            throw new LogicException("上传文件失败");
        }
    }

    private MinioClient buildMinioClient(){
        return MinioClient
                .builder()
                .credentials(minIOConfig.getAccessKey(),minIOConfig.getSecretKey())
                .endpoint(minIOConfig.getEndpoint())
                .build();
    }
}
