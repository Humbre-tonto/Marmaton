#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define TAG "MarmatonLlmJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct LlamaModelContext {
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_marmaton_agent_llm_GgufBackend_00024Companion_load(
        JNIEnv* env, jobject thiz, jstring model_path, jint n_ctx, jint n_threads) {
    if (!model_path) {
        LOGE("Model path is null");
        return 0;
    }

    const char* path_chars = env->GetStringUTFChars(model_path, nullptr);
    if (!path_chars) {
        LOGE("Failed to get model path characters");
        return 0;
    }

    LOGI("Loading model from: %s with n_ctx: %d, n_threads: %d", path_chars, n_ctx, n_threads);

    // Initialise llama backend (idempotent/safe to call multiple times)
    llama_backend_init();

    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0; // CPU only

    llama_model* model = llama_load_model_from_file(path_chars, mp);
    env->ReleaseStringUTFChars(model_path, path_chars);

    if (!model) {
        LOGE("Failed to load model from file");
        return 0;
    }

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = n_ctx;
    cp.n_threads = n_threads;
    // Also thread the prompt/batch evaluation. Without this it can fall back to a low default,
    // making the initial prompt eval (the bulk of per-step latency) painfully slow on phones.
    cp.n_threads_batch = n_threads;

    llama_context* ctx = llama_new_context_with_model(model, cp);
    if (!ctx) {
        LOGE("Failed to initialize llama context");
        llama_free_model(model);
        return 0;
    }

    auto* model_ctx = new LlamaModelContext();
    model_ctx->model = model;
    model_ctx->ctx = ctx;

    LOGI("Successfully loaded model and context: %p", model_ctx);
    return reinterpret_cast<jlong>(model_ctx);
}

JNIEXPORT jstring JNICALL
Java_com_marmaton_agent_llm_GgufBackend_00024Companion_generate(
        JNIEnv* env, jobject thiz, jlong handle, jstring prompt, jint max_tokens) {
    if (!handle) {
        LOGE("Invalid handle passed to generate");
        return env->NewStringUTF("");
    }

    auto* model_ctx = reinterpret_cast<LlamaModelContext*>(handle);
    if (!model_ctx->model || !model_ctx->ctx) {
        LOGE("Invalid model/context in handle");
        return env->NewStringUTF("");
    }

    const char* prompt_chars = env->GetStringUTFChars(prompt, nullptr);
    if (!prompt_chars) {
        LOGE("Failed to get prompt characters");
        return env->NewStringUTF("");
    }

    std::string prompt_str(prompt_chars);
    env->ReleaseStringUTFChars(prompt, prompt_chars);

    llama_model* model = model_ctx->model;
    llama_context* ctx = model_ctx->ctx;

    std::string formatted_prompt;

    // Apply chat template if possible
    std::vector<llama_chat_message> messages;
    messages.push_back({"user", prompt_str.c_str()});

    // Query required length for chat template formatting
    int32_t req_len = llama_chat_apply_template(model, nullptr, messages.data(), messages.size(), true, nullptr, 0);
    if (req_len > 0) {
        std::vector<char> buf(req_len + 1);
        int32_t formatted_len = llama_chat_apply_template(model, nullptr, messages.data(), messages.size(), true, buf.data(), buf.size());
        if (formatted_len > 0) {
            formatted_prompt = std::string(buf.data(), formatted_len);
        }
    }

    if (formatted_prompt.empty()) {
        formatted_prompt = prompt_str;
    }

    // Tokenize
    int32_t n_ctx = llama_n_ctx(ctx);
    // Find required token count
    int32_t n_prompt_tokens = -llama_tokenize(model, formatted_prompt.c_str(), formatted_prompt.size(), nullptr, 0, true, true);
    if (n_prompt_tokens <= 0) {
        // Fallback or retry with positive sizing
        n_prompt_tokens = n_ctx;
    }

    std::vector<llama_token> tokens(n_prompt_tokens);
    int32_t n_tokens = llama_tokenize(model, formatted_prompt.c_str(), formatted_prompt.size(), tokens.data(), tokens.size(), true, true);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(model, formatted_prompt.c_str(), formatted_prompt.size(), tokens.data(), tokens.size(), true, true);
    }

    if (n_tokens <= 0) {
        LOGE("Tokenization failed or prompt was empty");
        return env->NewStringUTF("");
    }

    // Truncate to context limit if needed (leaving room for max_tokens)
    if (n_tokens + max_tokens > n_ctx) {
        int32_t keep_tokens = n_ctx - max_tokens - 1;
        if (keep_tokens > 0) {
            tokens.erase(tokens.begin(), tokens.begin() + (n_tokens - keep_tokens));
            n_tokens = keep_tokens;
        } else {
            n_tokens = n_ctx - 1;
            tokens.resize(n_tokens);
        }
    }

    // Initialize sampler with greedy chain
    llama_sampler* smpl = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    // Decode prompt
    llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);
    if (llama_decode(ctx, batch) != 0) {
        LOGE("Failed to decode prompt tokens");
        llama_sampler_free(smpl);
        return env->NewStringUTF("");
    }

    std::string result = "";
    llama_token curr_token = llama_sampler_sample(smpl, ctx, -1);
    llama_sampler_accept(smpl, curr_token);

    int32_t count = 0;
    while (count < max_tokens && !llama_token_is_eog(model, curr_token)) {
        // Detokenize token
        char piece_buf[128];
        int32_t piece_len = llama_token_to_piece(model, curr_token, piece_buf, sizeof(piece_buf), 0, false);
        if (piece_len > 0) {
            result.append(piece_buf, piece_len);
        }

        // Decode next single token
        batch = llama_batch_get_one(&curr_token, 1);
        if (llama_decode(ctx, batch) != 0) {
            LOGE("Failed to decode intermediate token");
            break;
        }

        curr_token = llama_sampler_sample(smpl, ctx, -1);
        llama_sampler_accept(smpl, curr_token);
        count++;
    }

    llama_sampler_free(smpl);

    // Clean up KV cache of previous context evaluation to free resources for next runs
    llama_kv_cache_clear(ctx);

    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_marmaton_agent_llm_GgufBackend_00024Companion_free(
        JNIEnv* env, jobject thiz, jlong handle) {
    if (!handle) {
        return;
    }

    auto* model_ctx = reinterpret_cast<LlamaModelContext*>(handle);
    LOGI("Freeing model and context at: %p", model_ctx);

    if (model_ctx->ctx) {
        llama_free(model_ctx->ctx);
    }
    if (model_ctx->model) {
        llama_free_model(model_ctx->model);
    }
    delete model_ctx;
}

}
