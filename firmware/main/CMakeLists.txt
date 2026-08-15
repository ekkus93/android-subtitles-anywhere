idf_component_register(
    SRCS "main.c" "a2dp_sink.c" "audio_queue.c" "return_audio.c"
    INCLUDE_DIRS "."
    REQUIRES bt nvs_flash esp_timer
)
