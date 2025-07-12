package io.hhplus.tdd.error;

public class ExceedMaxChargeAmountException extends RuntimeException {

    public ExceedMaxChargeAmountException(String message) {
        super(message);
    }
}
