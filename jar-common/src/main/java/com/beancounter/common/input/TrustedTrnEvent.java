package com.beancounter.common.input;


import com.beancounter.common.model.Portfolio;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrustedTrnEvent implements TrnImport {
  private Portfolio portfolio;
  private String message;
  private TrnInput trnInput;

}
