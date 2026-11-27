package com.nirvana.application.service.impl;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.nirvana.application.exception.BusinessException;
import com.nirvana.application.config.SendGridProperties;
import com.nirvana.application.model.NotificationLog;
import com.nirvana.application.repository.NotificationLogRepository;
import com.sendgrid.SendGrid;
import com.sendgrid.Response;
import com.nirvana.application.model.dto.InvoiceEmailDto;
import com.nirvana.application.model.dto.MediaPresignRequest;
import com.nirvana.application.model.enums.MediaType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InputBoundariesTest {
    private static final String STAGED = "incoming/spa/17/21a86cf7-a3f2-41c1-9ce8-612887117ba8.png";

    private static byte[] newPng() throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(3, 4, BufferedImage.TYPE_INT_RGB), "png", bytes);
        return bytes.toByteArray();
    }

    @Test void unknownSortUsesOnlyCatalogFields() {
        assertEquals(SpaSearchService.buildSort(null), SpaSearchService.buildSort("unsupported-column"));
        assertEquals("ratingAvg", SpaSearchService.buildSort("rating_desc").get().findFirst().orElseThrow().getProperty());
    }

    @Test void invoiceRendersTextAndLinkInTheirOwnContexts() {
        var dto = InvoiceEmailDto.builder().toName("A & B").spaName("Green <Spa>")
                .serviceName("Massage & steam").invoicePdfUrl("https://example.test/invoice?a=1&b=2")
                .totalCents(28400).currency("INR").build();
        String html = InvoiceEmailContent.render(dto);
        assertTrue(html.contains("A &amp; B"));
        assertTrue(html.contains("Green &lt;Spa&gt;"));
        assertTrue(html.contains("https://example.test/invoice?a=1&amp;b=2"));
        assertFalse(InvoiceEmailContent.render(InvoiceEmailDto.builder().invoicePdfUrl("file:///receipt")
                .totalCents(0).build()).contains("<a href="));
    }

    @Test void invoiceDeliveryStoresOnlySafeTransportInformation() throws Exception {
        var provider = mock(SendGrid.class);
        var response = new Response(400, "fixture-private-provider-detail", java.util.Map.of());
        when(provider.api(any())).thenReturn(response);
        var properties = new SendGridProperties();
        properties.setFromEmail("sender@example.test");
        var logs = mock(NotificationLogRepository.class);
        var service = new SendGridEmailService(provider, properties, logs);
        var invoice = InvoiceEmailDto.builder().toEmail("customer@example.test").toName("Guest")
                .invoiceNumber("N-1").currency("INR").totalCents(100).build();
        service.sendInvoiceEmail(invoice);
        var entry = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logs).save(entry.capture());
        assertEquals("Email provider returned status 400", entry.getValue().getErrorMessage());
        var smtp = mock(org.springframework.mail.javamail.JavaMailSender.class);
        when(smtp.createMimeMessage()).thenThrow(new org.springframework.mail.MailSendException("fixture-private-provider-detail"));
        new NoOpEmailService(logs, properties, smtp).sendInvoiceEmail(invoice);
        verify(logs, times(2)).save(entry.capture());
        assertEquals("Email transport failed", entry.getValue().getErrorMessage());
    }

    @Test void privateStorageAcceptsRasterRejectsMismatchesAndTruncation() throws Exception {
        byte[] png = newPng();
        assertDoesNotThrow(() -> UploadFormat.PNG.verify(png));
        assertThrows(BusinessException.class, () -> UploadFormat.JPEG.verify(png));
        assertThrows(BusinessException.class, () -> UploadFormat.PNG.verify(Arrays.copyOf(png, 15)));
        assertThrows(BusinessException.class, () -> UploadFormat.read(new ByteArrayInputStream(png), png.length - 1));
        assertThrows(BusinessException.class, () -> UploadFormat.checkSize(UploadFormat.MAX_BYTES + 1L));
        var s3 = mock(AmazonS3.class);
        var service = new S3ServiceImpl(s3);
        var file = new MockMultipartFile("file", "menu.png", "image/png", "plain text".getBytes(StandardCharsets.UTF_8));
        assertThrows(BusinessException.class, () -> service.uploadFile(file));
        verifyNoInteractions(s3);
    }

    private static byte[] box(String kind, byte[] contents) {
        return ByteBuffer.allocate(contents.length + 8).putInt(contents.length + 8)
                .put(kind.getBytes(StandardCharsets.US_ASCII)).put(contents).array();
    }

    private static byte[] movie(String handler) throws Exception {
        var output = new ByteArrayOutputStream();
        output.write(box("ftyp", "isom0000isom".getBytes(StandardCharsets.US_ASCII)));
        output.write(box("moov", box("trak", box("mdia", box("hdlr",
                ("00000000" + handler).getBytes(StandardCharsets.US_ASCII))))));
        output.write(box("mdat", new byte[]{1, 2, 3}));
        return output.toByteArray();
    }

    @Test void requireVideoTrackAndBoundedContainerForMp4Uploads() throws Exception {
        byte[] video = movie("vide");
        assertDoesNotThrow(() -> UploadFormat.MP4.verify(video));
        assertThrows(BusinessException.class, () -> UploadFormat.MP4.verify(movie("soun")));
        assertThrows(BusinessException.class, () -> UploadFormat.MP4.verify(Arrays.copyOf(video, video.length - 1)));
    }

    @Test void signedPutBindsByteLengthMimeAndPrivateStagingKey() {
        // AWS signs this URL locally using fixture credentials. No request is sent.
        AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion("ap-south-1")
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("fixture-id","fixture-secret"))).build();
        try {
            var service = new S3ServiceImpl(s3);
            ReflectionTestUtils.setField(service, "bucketName", "fixture-media");
            var req = new MediaPresignRequest();
            req.setSpaId(17L); req.setMediaType(MediaType.IMAGE); req.setContentType("image/png");
            req.setFileName("untrusted-input.txt"); req.setSizeBytes(540L);
            var ticket = service.generatePreSignedUploadUrl(req);
            assertTrue(ticket.getObjectKey().matches("incoming/spa/17/[0-9a-f-]{36}\\.png"));
            String query = URLDecoder.decode(URI.create(ticket.getUploadUrl()).getRawQuery(), StandardCharsets.UTF_8);
            assertTrue(query.contains("X-Amz-SignedHeaders=content-length;content-type;host"));
            req.setSizeBytes(UploadFormat.MAX_BYTES + 1L);
            assertThrows(BusinessException.class, () -> service.generatePreSignedUploadUrl(req));
        } finally {
            s3.shutdown();
        }
    }

    @Test void mediaPublicationValidatesTenantAndActualBytesThenUsesAnIndependentKey() throws Exception {
        var s3 = mock(AmazonS3.class);
        var service = new S3ServiceImpl(s3);
        assertThrows(BusinessException.class, () -> service.publishMedia(18, STAGED, MediaType.IMAGE));
        assertNull(service.getSpaMediaUrl(17L, MediaType.IMAGE, "spa/18/images/menu.jpg"));
        assertNull(service.getSpaMediaUrl(17L, MediaType.IMAGE, STAGED));
        assertNull(service.getSpaMediaUrl(17L, MediaType.IMAGE, "private/files/document.pdf"));
        verifyNoInteractions(s3);
        byte[] png = newPng();
        var object = new S3Object();
        object.setObjectContent(new ByteArrayInputStream(png));
        object.getObjectMetadata().setContentLength(png.length);
        object.getObjectMetadata().setContentType("image/png");
        when(s3.getObject(null, STAGED)).thenReturn(object);
        String result = service.publishMedia(17, STAGED, MediaType.IMAGE);
        assertTrue(result.matches("spa/17/images/[a-f0-9-]{36}\\.png"));
        var capture = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(capture.capture());
        assertEquals(result, capture.getValue().getKey());
        assertArrayEquals(png, capture.getValue().getInputStream().readAllBytes());
        assertThrows(BusinessException.class, () -> service.publishMedia(17, result, MediaType.IMAGE));
    }
}
