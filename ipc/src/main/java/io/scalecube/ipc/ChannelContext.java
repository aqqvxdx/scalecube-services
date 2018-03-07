package io.scalecube.ipc;

import static io.scalecube.ipc.Event.Topic;

import io.scalecube.cluster.membership.IdGenerator;
import io.scalecube.transport.Address;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

public final class ChannelContext {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChannelContext.class);

  private static final Address HELPER_ADDRESS = Address.from("localhost:0");

  private static final ConcurrentMap<String, ChannelContext> idToChannelContext = new ConcurrentHashMap<>();

  private final Subject<Event, Event> subject = PublishSubject.<Event>create().toSerialized();
  private final Subject<Event, Event> closeSubject = PublishSubject.<Event>create().toSerialized();

  private final String id;
  private final Address address;

  private ChannelContext(String id, Address address) {
    Objects.requireNonNull(id);
    Objects.requireNonNull(address);
    this.id = id;
    this.address = address;
  }

  /**
   * Factory method for {@link ChannelContext} object. Id will be generated by
   * {@link io.scalecube.cluster.membership.IdGenerator}.
   * 
   * @param address an address for this channelContext.
   */
  public static ChannelContext create(Address address) {
    String id = IdGenerator.generateId();
    ChannelContext channelContext = new ChannelContext(id, address);
    idToChannelContext.put(id, channelContext);
    LOGGER.debug("Created {}", channelContext);
    return channelContext;
  }

  /**
   * Creates or gets {@link ChannelContext} by identity which is known upfront.
   * 
   * @param id known channelContext identity.
   * @return either newly created channelContext or existing one under this id in the map.
   */
  public static ChannelContext createOrGet(String id) {
    idToChannelContext.computeIfAbsent(id, id1 -> {
      ChannelContext channelContext = new ChannelContext(id1, HELPER_ADDRESS);
      LOGGER.debug("Created {}", channelContext);
      return channelContext;
    });
    return idToChannelContext.get(id);
  }

  /**
   * Creates special purpose {@link ChannelContext} instance with generated id and fixed address. See for details
   * internals of {@link ClientStream#send(Address, ServiceMessage)} function.
   */
  public static ChannelContext createHelper() {
    return create(HELPER_ADDRESS);
  }

  public static ChannelContext getIfExist(String id) {
    return idToChannelContext.get(id);
  }

  public static void closeIfExist(String id) {
    Optional.ofNullable(ChannelContext.getIfExist(id)).ifPresent(ChannelContext::close);
  }

  public String getId() {
    return id;
  }

  public Address getAddress() {
    return address;
  }

  public Observable<Event> listen() {
    return subject.onBackpressureBuffer().asObservable();
  }

  public Observable<Event> listenReadSuccess() {
    return listen().filter(Event::isReadSuccess);
  }

  public Observable<Event> listenReadError() {
    return listen().filter(Event::isReadError);
  }

  public Observable<Event> listenWrite() {
    return listen().filter(Event::isWrite);
  }

  public Observable<Event> listenWriteSuccess() {
    return listen().filter(Event::isWriteSuccess);
  }

  public Observable<Event> listenWriteError() {
    return listen().filter(Event::isWriteError);
  }

  public void postReadSuccess(ServiceMessage message) {
    subject.onNext(new Event.Builder(Topic.ReadSuccess, address, id).message(message).build());
  }

  public void postReadError(Throwable throwable) {
    subject.onNext(new Event.Builder(Topic.ReadError, address, id).error(throwable).build());
  }

  public void postWrite(ServiceMessage message) {
    subject.onNext(new Event.Builder(Topic.Write, address, id).message(message).build());
  }

  public void postWriteError(ServiceMessage message, Throwable throwable) {
    postWriteError(address, message, throwable);
  }

  public void postWriteError(Address address, ServiceMessage message, Throwable throwable) {
    subject.onNext(new Event.Builder(Topic.WriteError, address, id).error(throwable).message(message).build());
  }

  public void postWriteSuccess(ServiceMessage message) {
    subject.onNext(new Event.Builder(Topic.WriteSuccess, address, id).message(message).build());
  }

  /**
   * Issues close on this channel context: emits onCompleted on subject, and eventually removes itseld from the map.
   * Subsequent {@link #getIfExist(String)} would return null after this operation.
   */
  public void close() {
    subject.onCompleted();
    closeSubject.onCompleted();
    ChannelContext channelContext = idToChannelContext.remove(id);
    if (channelContext != null) {
      LOGGER.debug("Closed and removed {}", channelContext);
    }
  }

  /**
   * Set an action to take on a moment when this channelContext closed. Consumer param will be this channelContext with
   * completed subject.
   */
  public void listenClose(Consumer<ChannelContext> onClose) {
    closeSubject.subscribe(event -> {
    }, throwable -> onClose.accept(this), () -> onClose.accept(this));
  }

  @Override
  public String toString() {
    return "ChannelContext{id=" + id + ", address=" + address + "}";
  }
}
