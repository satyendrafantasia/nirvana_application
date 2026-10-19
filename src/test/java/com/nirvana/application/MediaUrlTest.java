package com.nirvana.application;
import com.amazonaws.services.s3.AmazonS3;
import com.nirvana.application.service.impl.S3ServiceImpl;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class MediaUrlTest {
 @Test void externalHttpsPhotosKeepTheirUrl(){var s3=mock(AmazonS3.class);var service=new S3ServiceImpl(s3);String url="https://images.pexels.com/photos/3230236/pexels-photo-3230236.jpeg?w=1200";assertEquals(url,service.getFileUrl(url));verifyNoInteractions(s3);}
 @Test void bucketKeysStillUseStorage() throws Exception {var s3=mock(AmazonS3.class);when(s3.getUrl(null,"spa/1/images/photo.jpg")).thenReturn(new java.net.URL("https://example.test/spa/1/images/photo.jpg"));assertEquals("https://example.test/spa/1/images/photo.jpg",new S3ServiceImpl(s3).getFileUrl("spa/1/images/photo.jpg"));}
}
