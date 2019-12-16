package com.beancounter.common.model;

import com.beancounter.common.utils.KeyGenUtils;
import java.util.UUID;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Owner of a collection of Positions.
 *
 * @author mikeh
 * @since 2019-01-28
 */
@Data
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
@Entity
//@Table(uniqueConstraints={@UniqueConstraint(columnNames={"CODE", "OWNER_ID"})})
public class Portfolio {
  @Builder.Default
  @Getter
  @Id
  private String id = KeyGenUtils.format(UUID.randomUUID());
  private String code;
  private String name;
  @ManyToOne
  private Currency currency;
  @Builder.Default
  @ManyToOne
  private Currency base = Currency.builder().code("USD").build();

//  @ManyToOne
//  private SystemUser owner;
}
