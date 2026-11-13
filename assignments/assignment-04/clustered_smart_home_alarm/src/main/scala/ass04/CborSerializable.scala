package ass04

/** Marker trait for every message that can cross a cluster node boundary.
  *
  * The binding is configured in `application.conf`:
  * `ass04.CborSerializable` is serialized with Jackson CBOR.
  */
trait CborSerializable
