package com.trading.fix.handler;

import com.trading.fix.service.OrderGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.ExecutionReport;
import quickfix.fix44.NewOrderSingle;
import quickfix.fix44.OrderCancelRequest;

/**
 * QuickFIX/J Application implementation
 * Handles FIX session lifecycle and message routing
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradingApplication implements Application {

    private final OrderGatewayService orderGatewayService;

    @Override
    public void onCreate(SessionID sessionId) {
        log.info("FIX Session created: {}", sessionId);
    }

    @Override
    public void onLogon(SessionID sessionId) {
        log.info("FIX Session logged on: {}", sessionId);
    }

    @Override
    public void onLogout(SessionID sessionId) {
        log.info("FIX Session logged out: {}", sessionId);
    }

    @Override
    public void toAdmin(Message message, SessionID sessionId) {
        log.debug("Sending admin message to {}: {}", sessionId, message);
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionId) 
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        log.debug("Received admin message from {}: {}", sessionId, message);
    }

    @Override
    public void toApp(Message message, SessionID sessionId) throws DoNotSend {
        log.debug("Sending app message to {}: {}", sessionId, message);
    }

    @Override
    public void fromApp(Message message, SessionID sessionId) 
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        
        log.info("Received app message from {}: {}", sessionId, message);
        
        try {
            crack(message, sessionId);
        } catch (Exception e) {
            log.error("Error processing message from {}", sessionId, e);
            sendReject(sessionId, message, e.getMessage());
        }
    }

    /**
     * Route message to appropriate handler
     */
    private void crack(Message message, SessionID sessionId) 
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        
        String msgType = message.getHeader().getString(MsgType.FIELD);
        
        switch (msgType) {
            case MsgType.ORDER_SINGLE -> onMessage((NewOrderSingle) message, sessionId);
            case MsgType.ORDER_CANCEL_REQUEST -> onMessage((OrderCancelRequest) message, sessionId);
            default -> throw new UnsupportedMessageType("Unsupported message type: " + msgType);
        }
    }

    /**
     * Handle NewOrderSingle (35=D)
     */
    public void onMessage(NewOrderSingle message, SessionID sessionId) throws FieldNotFound {
        log.info("Processing NewOrderSingle from {}", sessionId);
        
        String clOrdID = message.getClOrdID().getValue();
        String symbol = message.getSymbol().getValue();
        char side = message.getSide().getValue();
        char ordType = message.getOrdType().getValue();
        double quantity = message.getOrderQty().getValue();
        
        Double price = null;
        if (message.isSetField(Price.FIELD)) {
            price = message.getPrice().getValue();
        }
        
        String account = message.isSetField(Account.FIELD) ? 
            message.getAccount().getValue() : "DEFAULT";
        
        try {
            orderGatewayService.handleNewOrder(
                sessionId, clOrdID, symbol, side, ordType, quantity, price, account
            );
        } catch (Exception e) {
            log.error("Error handling new order", e);
            sendExecutionReport(sessionId, clOrdID, symbol, 
                ExecType.REJECTED, OrdStatus.REJECTED, e.getMessage());
        }
    }

    /**
     * Handle OrderCancelRequest (35=F)
     */
    public void onMessage(OrderCancelRequest message, SessionID sessionId) throws FieldNotFound {
        log.info("Processing OrderCancelRequest from {}", sessionId);
        
        String origClOrdID = message.getOrigClOrdID().getValue();
        String clOrdID = message.getClOrdID().getValue();
        String symbol = message.getSymbol().getValue();
        
        try {
            orderGatewayService.handleCancelOrder(sessionId, origClOrdID, clOrdID, symbol);
        } catch (Exception e) {
            log.error("Error handling cancel request", e);
            sendCancelReject(sessionId, clOrdID, origClOrdID, e.getMessage());
        }
    }

    /**
     * Send ExecutionReport (35=8)
     */
    public void sendExecutionReport(
            SessionID sessionId,
            String clOrdID,
            String symbol,
            char execType,
            char ordStatus,
            String text) {
        
        try {
            ExecutionReport report = new ExecutionReport(
                new OrderID(clOrdID),
                new ExecID(java.util.UUID.randomUUID().toString()),
                new ExecType(execType),
                new OrdStatus(ordStatus),
                new Side(Side.BUY),
                new LeavesQty(0),
                new CumQty(0),
                new AvgPx(0)
            );
            
            report.set(new ClOrdID(clOrdID));
            report.set(new Symbol(symbol));
            
            if (text != null && !text.isEmpty()) {
                report.set(new Text(text));
            }
            
            Session.sendToTarget(report, sessionId);
            log.info("Sent ExecutionReport to {}: {}", sessionId, report);
            
        } catch (Exception e) {
            log.error("Error sending execution report", e);
        }
    }

    /**
     * Send OrderCancelReject (35=9)
     */
    private void sendCancelReject(
            SessionID sessionId,
            String clOrdID,
            String origClOrdID,
            String reason) {
        
        try {
            quickfix.fix44.OrderCancelReject reject = new quickfix.fix44.OrderCancelReject(
                new OrderID(origClOrdID),
                new ClOrdID(clOrdID),
                new OrigClOrdID(origClOrdID),
                new OrdStatus(OrdStatus.REJECTED),
                new CxlRejResponseTo(CxlRejResponseTo.ORDER_CANCEL_REQUEST)
            );
            
            reject.set(new Text(reason));
            
            Session.sendToTarget(reject, sessionId);
            log.info("Sent OrderCancelReject to {}", sessionId);
            
        } catch (Exception e) {
            log.error("Error sending cancel reject", e);
        }
    }

    /**
     * Send Reject message
     */
    private void sendReject(SessionID sessionId, Message message, String reason) {
        try {
            quickfix.fix44.Reject reject = new quickfix.fix44.Reject();
            reject.set(new Text(reason));
            
            Session.sendToTarget(reject, sessionId);
            log.info("Sent Reject to {}: {}", sessionId, reason);
            
        } catch (Exception e) {
            log.error("Error sending reject", e);
        }
    }
}

// Made with Bob
