package com.nirvana.application.service.impl;

import com.nirvana.application.exception.BusinessException;
import com.nirvana.application.model.enums.MediaType;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

enum UploadFormat {
    JPEG("image/jpeg", "jpg", MediaType.IMAGE), PNG("image/png", "png", MediaType.IMAGE),
    WEBP("image/webp", "webp", MediaType.IMAGE), MP4("video/mp4", "mp4", MediaType.VIDEO),
    PDF("application/pdf", "pdf", null);

    static final int MAX_BYTES = 10 * 1024 * 1024;
    final String mime;
    final String extension;
    final MediaType kind;

    UploadFormat(String mime, String extension, MediaType kind) {
        this.mime = mime;
        this.extension = extension;
        this.kind = kind;
    }

    static UploadFormat forMime(String mime) {
        return Arrays.stream(values()).filter(format -> format.mime.equals(mime)).findFirst()
                .orElseThrow(() -> new BusinessException("Unsupported file format"));
    }

    static void checkSize(long size) {
        if (size < 1 || size > MAX_BYTES) throw new BusinessException("Choose a file between 1 byte and 10 MiB");
    }

    static byte[] read(InputStream in, long length) throws IOException {
        checkSize(length);
        byte[] bytes = in.readNBytes((int) length);
        if (bytes.length != length || in.read() != -1) throw new BusinessException("Upload length mismatch");
        return bytes;
    }

    void verify(byte[] data) {
        checkSize(data.length);
        boolean valid = switch (this) {
            case JPEG, PNG -> isRaster(data);
            case WEBP -> isWebp(data);
            case MP4 -> isMp4(data);
            case PDF -> tag(data, 0, "%PDF-") && new String(data, Math.max(0, data.length - 128),
                    Math.min(data.length, 128), StandardCharsets.ISO_8859_1).stripTrailing().endsWith("%%EOF");
        };
        if (!valid) throw new BusinessException("File content does not match the selected format");
    }

    private boolean isRaster(byte[] data) {
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) return false;
            ImageReader reader = readers.next();
            try {
                if (!reader.getFormatName().equalsIgnoreCase(name())) return false;
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if ((long) width * height > 24_000_000 || width < 1 || height < 1) return false;
                var params = reader.getDefaultReadParam();
                params.setSourceSubsampling(8, 8, 0, 0);
                reader.read(0, params);
                return this == JPEG ? data.length >= 2 && (data[data.length - 2] & 255) == 255 && (data[data.length - 1] & 255) == 217
                        : data.length >= 12 && tag(data, data.length - 8, "IEND");
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException invalid) {
            return false;
        }
    }

    private static boolean tag(byte[] data, int offset, String tag) {
        if (offset < 0 || offset + tag.length() > data.length) return false;
        for (int i = 0; i < tag.length(); i++) if (data[offset + i] != tag.charAt(i)) return false;
        return true;
    }

    private static boolean isWebp(byte[] data) {
        if (data.length < 30 || !tag(data, 0, "RIFF") || !tag(data, 8, "WEBP")) return false;
        var buffer = ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        if (buffer.getInt(4) != data.length - 8) return false;
        boolean image = false;
        for (int pos = 12; pos < data.length;) {
            if (pos + 8 > data.length) return false;
            long size = Integer.toUnsignedLong(buffer.getInt(pos + 4));
            long next = pos + 8L + size + size % 2;
            if (next > data.length) return false;
            image |= (size >= 10 && tag(data, pos, "VP8 ") && (data[pos + 8] & 1) == 0
                    && (data[pos + 11] & 255) == 157 && data[pos + 12] == 1 && data[pos + 13] == 42)
                    || (size >= 5 && tag(data, pos, "VP8L") && data[pos + 8] == 47);
            pos = (int) next;
        }
        return image;
    }

    private static boolean isMp4(byte[] data) {
        if (data.length < 24 || !tag(data, 4, "ftyp")) return false;
        var buffer = ByteBuffer.wrap(data);
        boolean brand = false, movie = false, media = false;
        for (int pos = 0; pos < data.length;) {
            if (pos + 8 > data.length) return false;
            long size = Integer.toUnsignedLong(buffer.getInt(pos));
            if (size == 0) size = data.length - pos;
            if (size < 8 || size > data.length - pos) return false;
            if (pos == 0) {
                if (size < 16) return false;
                var brands = Set.of("isom","iso2","mp41","mp42","avc1","M4V ");
                for (int p = 8; p + 4 <= size; p += 4) {
                    if (p != 12 && brands.contains(new String(data, p, 4, StandardCharsets.US_ASCII))) brand = true;
                }
            }
            movie |= tag(data, pos + 4, "moov") && hasVideoTrack(data, buffer, pos + 8, pos + (int) size, 0);
            media |= tag(data, pos + 4, "mdat") && size > 8;
            pos += (int) size;
        }
        return brand && movie && media;
    }
    // A media-data atom alone can also be an audio file. Require a video handler in the track metadata.
    private static boolean hasVideoTrack(byte[] data, ByteBuffer buffer, int start, int end, int level) {
        String expected = switch (level) { case 0 -> "trak"; case 1 -> "mdia"; default -> "hdlr"; };
        for (int pos = start; pos + 8 <= end;) {
            long length = Integer.toUnsignedLong(buffer.getInt(pos));
            if (length < 8 || length > end - pos) return false;
            if (tag(data, pos + 4, expected)) {
                if (level == 2 && length >= 20 && tag(data, pos + 16, "vide")) return true;
                if (level < 2 && hasVideoTrack(data, buffer, pos + 8, pos + (int) length, level + 1)) return true;
            }
            pos += (int) length;
        }
        return false;
    }
}
