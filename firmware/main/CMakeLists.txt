idf_component_register(
    SRCS "main.c" "a2dp_sink.c" "audio_queue.c" "return_audio.c"
         "protocol_v1.c" "transport_state.c" "uart_transport.c"
    INCLUDE_DIRS "."
    REQUIRES bt nvs_flash esp_timer esp_driver_uart
)
