package com.microsoft.azure.functions.worker.handler;

import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.*;

import com.google.protobuf.*;
import org.apache.commons.lang3.*;
import org.apache.commons.lang3.exception.*;

import com.microsoft.azure.functions.worker.*;
import com.microsoft.azure.functions.rpc.messages.*;

/**
 * Generic base class for all message handlers. It does the input marshaling, business logic as well as the output marshaling.
 * Thread-Safety: Single thread.
 * @param <TRequest> The request Grpc message type of the message.
 * @param <TResponse> The response Grpc message type of the message.
 */
public abstract class MessageHandler<TRequest extends Message, TResponse extends Message.Builder> {
    MessageHandler(Function<StreamingMessage, TRequest> requestMarshaller,
                   Supplier<TResponse> responseSupplier,
                   BiConsumer<TResponse, StatusResult> responseStatusMarshaller,
                   BiConsumer<StreamingMessage.Builder, TResponse> responseMarshaller) {
        this.requestMarshaller = requestMarshaller;
        this.responseSupplier = responseSupplier;
        this.responseStatusMarshaller = responseStatusMarshaller;
        this.responseMarshaller = responseMarshaller;
    }

    public void setRequest(StreamingMessage message) {
        this.request = this.requestMarshaller.apply(message);
    }

    public void marshalResponse(StreamingMessage.Builder message) {
        this.responseMarshaller.accept(message, this.response);
    }

    public void handle() {
        StatusResult.Status status = StatusResult.Status.Success;
        String statusMessage;
        RpcException rpcException = null;
        try {
            this.response = this.responseSupplier.get();
            statusMessage = this.execute(this.request, this.response);
            if (statusMessage != null) {
                this.getLogger().info(statusMessage);
            }
        } catch (Exception ex) {
            status = StatusResult.Status.Failure;
            statusMessage = ex.getMessage();
            rpcException = RpcException.newBuilder().setMessage(ex.getMessage()).setStackTrace(ExceptionUtils.getStackTrace(ex)).build();
            this.getLogger().log(Level.SEVERE, statusMessage, ex);
        }
        if (this.responseStatusMarshaller != null) {
            StatusResult.Builder result = StatusResult.newBuilder().setStatus(status).setResult(statusMessage);
            if (rpcException != null) {
                result = result.setException(rpcException);
            }
            this.responseStatusMarshaller.accept(this.response, result.build());
        }
    }

    public void registerTask(Future<?> task) { }
    Logger getLogger() { return WorkerLogManager.getHostLogger(); }
    abstract String execute(TRequest request, TResponse response) throws Exception;

    private TRequest request = null;
    private TResponse response = null;
    private final Function<StreamingMessage, TRequest> requestMarshaller;
    private final Supplier<TResponse> responseSupplier;
    private final BiConsumer<TResponse, StatusResult> responseStatusMarshaller;
    private final BiConsumer<StreamingMessage.Builder, TResponse> responseMarshaller;
}

abstract class OutboundMessageHandler<T extends Message.Builder> extends MessageHandler<Message, T> {
    OutboundMessageHandler(Supplier<T> responseSupplier, BiConsumer<StreamingMessage.Builder, T> responseMarshaller) {
        super(null, responseSupplier, null, responseMarshaller);
        super.handle();
    }

    @Override
    String execute(Message message, T response) {
        return "Message generated by \"" + ClassUtils.getShortClassName(response.getClass()) + "\"";
    }
}
