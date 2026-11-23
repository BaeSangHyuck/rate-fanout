package dev.sanghyuck.ratefanout.common.jpa

import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Transient
import org.springframework.data.domain.Persistable

// id를 직접 넣는 엔티티는 save()가 merge로 빠져서 select를 한 번 더 하고, 중복 id도 insert 대신 update가 된다.
// 새로 만든 객체는 persist로 가게 해서 PK 중복이 그대로 예외로 올라오게 한다
@MappedSuperclass
abstract class AssignedIdEntity : Persistable<String> {
  @Transient
  private var newEntity = true

  override fun isNew(): Boolean = newEntity

  @PostPersist
  @PostLoad
  fun markNotNew() {
    newEntity = false
  }
}
