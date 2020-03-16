package com.beancounter.shell.sharesight;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.identity.TrnId;
import com.beancounter.common.input.TrnInput;
import com.beancounter.common.model.Portfolio;
import com.beancounter.shell.ingest.RowProcessor;
import com.beancounter.shell.ingest.Transformer;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ShareSightRowProcessor implements RowProcessor {

  private ShareSightTransformers shareSightTransformers;

  @Autowired
  void setShareSightTransformers(ShareSightTransformers shareSightTransformers) {
    this.shareSightTransformers = shareSightTransformers;
  }

  @Override
  public Collection<TrnInput> transform(Portfolio portfolio,
                                        List<List<Object>> values,
                                        String provider) {

    Collection<TrnInput> results = new ArrayList<>();

    int trnId = 1;
    for (List<Object> row : values) {
      Transformer transformer = shareSightTransformers.transformer(row);

      try {
        if (transformer.isValid(row)) {
          TrnInput trn = transformer.from(row, portfolio);
          if (trn != null) {
            trn.setId(TrnId.builder()
                .id(trnId++)
                .batch(0)
                .provider(provider)
                .build());
            results.add(trn);
          }
        }
      } catch (ParseException | NumberFormatException e) {
        log.error("{} Parsing row {} - {}", transformer.getClass().getSimpleName(), trnId, row);
        throw new BusinessException(e.getMessage());
      }

    }
    return results;
  }

}
