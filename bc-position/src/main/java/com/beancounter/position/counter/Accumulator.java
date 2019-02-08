package com.beancounter.position.counter;

import com.beancounter.common.model.Position;
import com.beancounter.common.model.Quantity;
import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * @author mikeh
 * @since 2019-02-07
 */
@Service
public class Accumulator {

    public Position accumulate(Transaction transaction, Position position){
        if ( transaction.getTrnType().equals(TrnType.BUY))
            return buySide(transaction, position);
        else if ( transaction.getTrnType().equals(TrnType.SELL))
            return sellSide(transaction, position);
        return position;
    }

    private Position buySide(Transaction transaction, Position position){
        Quantity quantity = position.getQuantity();
        quantity.setPurchased( quantity.getPurchased().add(transaction.getQuantity()));
        position.getMoneyValues().setMarketCost(
            position.getMoneyValues().getMarketCost().add(transaction.getTradeAmount()));
        return position;
    }

    private Position sellSide(Transaction transaction, Position position){
        BigDecimal qSold = transaction.getQuantity();
        if (qSold.doubleValue() > 0 )
            qSold = new BigDecimal(0-transaction.getQuantity().doubleValue()) ;

        Quantity quantity = position.getQuantity();
        quantity.setSold( quantity.getSold().add(qSold));

        return position;
    }

}
