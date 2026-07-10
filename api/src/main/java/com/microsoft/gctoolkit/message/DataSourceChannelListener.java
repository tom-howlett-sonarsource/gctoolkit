package com.microsoft.gctoolkit.message;

public interface DataSourceChannelListener extends ChannelListener<String> {

    String END_OF_DATA_SENTINEL = "END_OF_DATA_SENTINEL";

    @Override
    void receive(String payload);
}
