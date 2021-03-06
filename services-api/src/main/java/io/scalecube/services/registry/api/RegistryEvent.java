package io.scalecube.services.registry.api;

import io.scalecube.services.ServiceReference;
import io.scalecube.transport.Address;

public class RegistryEvent {

  public enum Type {
    ADDED,
    REMOVED;
  }

  private ServiceReference serviceReference;
  private Type type;

  public static RegistryEvent createAdded(ServiceReference serviceReference) {
    return new RegistryEvent(Type.ADDED, serviceReference);
  }

  public static RegistryEvent createRemoved(ServiceReference serviceReference) {
    return new RegistryEvent(Type.REMOVED, serviceReference);
  }

  private RegistryEvent(Type type, ServiceReference serviceReference) {
    this.serviceReference = serviceReference;
    this.type = type;
  }

  private RegistryEvent(RegistryEvent e) {
    this.serviceReference = e.serviceReference;
    this.type = e.type;
  }

  public ServiceReference serviceReference() {
    return this.serviceReference;
  }

  public boolean isAdded() {
    return Type.ADDED.equals(type);
  }

  public boolean isRemoved() {
    return Type.REMOVED.equals(type);
  }

  public Type type() {
    return this.type;
  }

  public Address address() {
    return Address.create(this.serviceReference.host(), this.serviceReference.port());
  }
}
