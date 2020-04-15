package com.beancounter.common.contracts;

import com.beancounter.common.input.AssetInput;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AssetRequest implements Payload<Map<String, AssetInput>> {
  @Singular("data")
  private Map<String, AssetInput> data;

}
