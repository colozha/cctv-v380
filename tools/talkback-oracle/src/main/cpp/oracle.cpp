#include <jni.h>
#include <dlfcn.h>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <stdexcept>
#include <string>
#include <vector>

// ABI recovered from the hash-pinned ARM64 library. This tool never creates a player/socket.
template<class T> T symbol(void* library, const char* name) {
    auto address = dlsym(library, name);
    if (!address) throw std::runtime_error(std::string("Missing symbol: ") + name);
    return reinterpret_cast<T>(address);
}
template<class T> void set(uint8_t* target, size_t offset, T value) { memcpy(target + offset, &value, sizeof(value)); }
template<class T> T get(uint8_t* target, size_t offset) { T value; memcpy(&value, target + offset, sizeof(value)); return value; }
std::string hex(const uint8_t* bytes, size_t length) {
    const char* digits = "0123456789abcdef";
    std::string result(length * 2, '0');
    for (size_t i = 0; i < length; ++i) { result[i * 2] = digits[bytes[i] >> 4]; result[i * 2 + 1] = digits[bytes[i] & 15]; }
    return result;
}

extern "C" JNIEXPORT jstring JNICALL Java_dev_cctv_v380_oracle_OracleRunner_vectors(JNIEnv* env, jclass) {
    try {
        void* media = dlopen("libhsMediaLibrary.so", RTLD_NOW | RTLD_LOCAL);
        if (!media) throw std::runtime_error(dlerror());
        auto initEncoder = symbol<void*(*)(int, void*)>(media, "initMediaEncoder");
        auto encode = symbol<int(*)(void**, void*, void*)>(media, "encodeFrameData");
        auto release = symbol<void(*)(void*)>(media, "releaseMediaEncoder");
        auto initCipher = symbol<int(*)(void*, uint8_t, uint8_t, uint8_t*, uint8_t*)>(media,
            "_Z25iotc_ms_frame_cipher_initP30_media_stream_frame_trans_infohhPhS1_");
        auto cipher = symbol<int(*)(void*, uint8_t)>(media, "_Z20iotc_ms_frame_cipherP30_media_stream_frame_trans_infoh");
        auto endCipher = symbol<int(*)(void*)>(media, "_Z24iotc_ms_frame_cipher_endP30_media_stream_frame_trans_info");
        auto envelope = symbol<int(*)(uint8_t*, int, int, int, int, int, int, uint8_t*, int)>(media,
            "_Z24initCommonDataSendBufferPciiiiiiS_i");
        uint8_t key[32]; for (int i = 0; i < 32; ++i) key[i] = i; // Public synthetic key.
        void* encoder = initEncoder(2001, nullptr);
        if (!encoder) throw std::runtime_error("ADPCM encoder init failed");
        std::string json = "{\"syntheticOnly\":true,\"abi\":\"arm64-v8a\",\"vectors\":[";
        const char* names[] = {"silence", "ramp", "tone", "constant", "tone_continued", "restart_tone", "silence_after_signal"};
        for (int n = 0; n < 7; ++n) {
            if (n == 5) {
                release(encoder); encoder = initEncoder(2001, nullptr);
                if (!encoder) throw std::runtime_error("ADPCM encoder restart failed");
            }
            std::vector<uint8_t> pcm(1010);
            for (int i = 0; i < 505; ++i) {
                int16_t value = 0;
                if (n == 1) value = i * 4 - 1008;
                if (n == 2 || n == 4 || n == 5) value = static_cast<int16_t>(2000 * sin(2 * 3.141592653589793 * 440 * i / 16000));
                if (n == 3) value = 1000;
                set(pcm.data(), i * 2, value);
            }
            alignas(16) uint8_t input[64] = {}, output[64] = {};
            std::vector<uint8_t> encoded(4096);
            set(input, 0x2c, int(1010)); set(input, 0x30, pcm.data()); set(output, 0x30, encoded.data());
            int count = encode(&encoder, input, output);
            if (count != 256 || get<int>(output, 0x2c) != 256) throw std::runtime_error("Unexpected ADPCM size");
            if (n) json += ",";
            json += "{\"name\":\"" + std::string(names[n]) + "\",\"pcm\":\"" + hex(pcm.data(), pcm.size()) +
                "\",\"encoded\":\"" + hex(encoded.data(), count) + "\",\"packets\":[";
            // Test raw PCM100 and encoded PCM21 independently through the same native cipher/envelope.
            for (int mode = 0; mode < 2; ++mode) {
                uint8_t* payload = mode ? encoded.data() : pcm.data();
                int size = mode ? count : static_cast<int>(pcm.size());
                alignas(16) uint8_t frame[0x140] = {};
                frame[1] = 4; frame[0x25] = 1; frame[0x26] = 1;
                set(frame, 4, uint32_t(n + 1)); set(frame, 8, size); set(frame, 0x10, uint64_t(1000 + n * 32));
                set(frame, 0x28, payload); set(frame, 0x30, size);
                if (initCipher(frame, 2, 1, key, key + 16) < 0 || cipher(frame, 1) < 0)
                    throw std::runtime_error("Native frame cipher failed");
                auto body = get<uint8_t*>(frame, 0x138); int bodySize = get<int>(frame, 0x34);
                if (bodySize != size + 32) throw std::runtime_error("Unexpected encrypted size");
                std::vector<uint8_t> wire(bodySize + 16, 0);
                int wireSize = envelope(wire.data(), 0, 939722784, 1, 2, 17, 0, body, bodySize);
                if (wireSize != static_cast<int>(wire.size())) throw std::runtime_error("Unexpected envelope size");
                if (mode) json += ",";
                json += "\"" + hex(wire.data(), wire.size()) + "\"";
                endCipher(frame);
            }
            json += "]}";
        }
        release(encoder);
        json += "]}";
        return env->NewStringUTF(json.c_str());
    } catch (const std::exception& error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
        return nullptr;
    }
}
