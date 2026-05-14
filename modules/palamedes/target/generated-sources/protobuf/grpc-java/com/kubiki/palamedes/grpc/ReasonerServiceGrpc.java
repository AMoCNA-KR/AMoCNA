package com.kubiki.palamedes.grpc;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.62.2)",
    comments = "Source: palamedes.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class ReasonerServiceGrpc {

  private ReasonerServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "palamedes.ReasonerService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.kubiki.palamedes.grpc.ResourceUpdate,
      com.kubiki.palamedes.grpc.TriggerResponse> getTriggerUpdateMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "TriggerUpdate",
      requestType = com.kubiki.palamedes.grpc.ResourceUpdate.class,
      responseType = com.kubiki.palamedes.grpc.TriggerResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.kubiki.palamedes.grpc.ResourceUpdate,
      com.kubiki.palamedes.grpc.TriggerResponse> getTriggerUpdateMethod() {
    io.grpc.MethodDescriptor<com.kubiki.palamedes.grpc.ResourceUpdate, com.kubiki.palamedes.grpc.TriggerResponse> getTriggerUpdateMethod;
    if ((getTriggerUpdateMethod = ReasonerServiceGrpc.getTriggerUpdateMethod) == null) {
      synchronized (ReasonerServiceGrpc.class) {
        if ((getTriggerUpdateMethod = ReasonerServiceGrpc.getTriggerUpdateMethod) == null) {
          ReasonerServiceGrpc.getTriggerUpdateMethod = getTriggerUpdateMethod =
              io.grpc.MethodDescriptor.<com.kubiki.palamedes.grpc.ResourceUpdate, com.kubiki.palamedes.grpc.TriggerResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "TriggerUpdate"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kubiki.palamedes.grpc.ResourceUpdate.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kubiki.palamedes.grpc.TriggerResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ReasonerServiceMethodDescriptorSupplier("TriggerUpdate"))
              .build();
        }
      }
    }
    return getTriggerUpdateMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ReasonerServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ReasonerServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ReasonerServiceStub>() {
        @java.lang.Override
        public ReasonerServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ReasonerServiceStub(channel, callOptions);
        }
      };
    return ReasonerServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ReasonerServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ReasonerServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ReasonerServiceBlockingStub>() {
        @java.lang.Override
        public ReasonerServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ReasonerServiceBlockingStub(channel, callOptions);
        }
      };
    return ReasonerServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ReasonerServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ReasonerServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ReasonerServiceFutureStub>() {
        @java.lang.Override
        public ReasonerServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ReasonerServiceFutureStub(channel, callOptions);
        }
      };
    return ReasonerServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void triggerUpdate(com.kubiki.palamedes.grpc.ResourceUpdate request,
        io.grpc.stub.StreamObserver<com.kubiki.palamedes.grpc.TriggerResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getTriggerUpdateMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service ReasonerService.
   */
  public static abstract class ReasonerServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return ReasonerServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service ReasonerService.
   */
  public static final class ReasonerServiceStub
      extends io.grpc.stub.AbstractAsyncStub<ReasonerServiceStub> {
    private ReasonerServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ReasonerServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ReasonerServiceStub(channel, callOptions);
    }

    /**
     */
    public void triggerUpdate(com.kubiki.palamedes.grpc.ResourceUpdate request,
        io.grpc.stub.StreamObserver<com.kubiki.palamedes.grpc.TriggerResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getTriggerUpdateMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service ReasonerService.
   */
  public static final class ReasonerServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<ReasonerServiceBlockingStub> {
    private ReasonerServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ReasonerServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ReasonerServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.kubiki.palamedes.grpc.TriggerResponse triggerUpdate(com.kubiki.palamedes.grpc.ResourceUpdate request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getTriggerUpdateMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service ReasonerService.
   */
  public static final class ReasonerServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<ReasonerServiceFutureStub> {
    private ReasonerServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ReasonerServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ReasonerServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.kubiki.palamedes.grpc.TriggerResponse> triggerUpdate(
        com.kubiki.palamedes.grpc.ResourceUpdate request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getTriggerUpdateMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_TRIGGER_UPDATE = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_TRIGGER_UPDATE:
          serviceImpl.triggerUpdate((com.kubiki.palamedes.grpc.ResourceUpdate) request,
              (io.grpc.stub.StreamObserver<com.kubiki.palamedes.grpc.TriggerResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getTriggerUpdateMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.kubiki.palamedes.grpc.ResourceUpdate,
              com.kubiki.palamedes.grpc.TriggerResponse>(
                service, METHODID_TRIGGER_UPDATE)))
        .build();
  }

  private static abstract class ReasonerServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ReasonerServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.kubiki.palamedes.grpc.PalamedesProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ReasonerService");
    }
  }

  private static final class ReasonerServiceFileDescriptorSupplier
      extends ReasonerServiceBaseDescriptorSupplier {
    ReasonerServiceFileDescriptorSupplier() {}
  }

  private static final class ReasonerServiceMethodDescriptorSupplier
      extends ReasonerServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    ReasonerServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (ReasonerServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ReasonerServiceFileDescriptorSupplier())
              .addMethod(getTriggerUpdateMethod())
              .build();
        }
      }
    }
    return result;
  }
}
