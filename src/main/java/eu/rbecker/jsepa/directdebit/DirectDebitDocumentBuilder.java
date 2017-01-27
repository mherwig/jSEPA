/*
 *  All rights reserved.
 */
package eu.rbecker.jsepa.directdebit;

import eu.rbecker.jsepa.directdebit.util.SepaXmlDocumentBuilder;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.AccountIdentificationSEPA;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.ActiveOrHistoricCurrencyAndAmountSEPA;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.ActiveOrHistoricCurrencyCodeEUR;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.CashAccountSEPA1;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.CashAccountSEPA2;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.ChargeBearerTypeSEPACode;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.CustomerDirectDebitInitiationV02;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.DirectDebitTransactionInformationSDD;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.DirectDebitTransactionSDD;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.Document;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.GroupHeaderSDD;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.IdentificationSchemeNameSEPA;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.LocalInstrumentSEPA;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.MandateRelatedInformationSDD;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.ObjectFactory;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.PartyIdentificationSEPA1;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.PartyIdentificationSEPA2;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.PartyIdentificationSEPA3;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.PartyIdentificationSEPA5;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.PartySEPA2;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.PaymentIdentificationSEPA;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.PaymentInstructionInformationSDD;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.PaymentMethod2Code;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.PaymentTypeInformationSDD;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.PersonIdentificationSEPA2;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.RemittanceInformationSEPA1Choice;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.RestrictedPersonIdentificationSEPA;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.RestrictedPersonIdentificationSchemeNameSEPA;
import eu.rbecker.jsepa.directdebit.xml.schema.pain_008_003_02.ServiceLevelSEPA;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.List;
import javax.xml.datatype.DatatypeConfigurationException;

/**
 *
 * @author Robert Becker <robert at rbecker.eu>
 */
class DirectDebitDocumentBuilder extends SepaXmlDocumentBuilder {

    public static String toXml(DirectDebitDocumentData ddd) throws DatatypeConfigurationException {
        // sepa xml document
        Document doc = new Document();
        // CustomerDirectDebitInitiationV02
        CustomerDirectDebitInitiationV02 cddiv = new CustomerDirectDebitInitiationV02();
        doc.setCstmrDrctDbtInitn(cddiv);

        // group header
        cddiv.setGrpHdr(createGroupHeaderSdd(ddd));

        cddiv.getPmtInf().addAll(createPaymentInstructions(ddd));

        // marshal to string
        StringWriter resultWriter = new StringWriter();
        marshal(doc.getClass().getPackage().getName(), new ObjectFactory().createDocument(doc), resultWriter);
        return resultWriter.toString();
    }

    private static List<PaymentInstructionInformationSDD> createPaymentInstructions(DirectDebitDocumentData ddd) throws DatatypeConfigurationException {
        List<PaymentInstructionInformationSDD> result = new ArrayList<>();

        for (MandateType mt : MandateType.values()) {
            if (ddd.getNumberOfPaymentsByMandateType(mt) > 0) {
                result.add(createPaymentInstructionInformation(ddd, mt));
            }
        }

        return result;
    }

    private static PaymentInstructionInformationSDD createPaymentInstructionInformation(DirectDebitDocumentData ddd, MandateType mandateType) throws DatatypeConfigurationException {

        PaymentInstructionInformationSDD result = new PaymentInstructionInformationSDD();
        // payment information id
        result.setPmtInfId(ddd.getDocumentMessageId());
        // payment method (fixed)
        result.setPmtMtd(PaymentMethod2Code.DD);
        // batch booking (fixed)
        result.setBtchBookg(Boolean.TRUE);

        // number of transactions
        result.setNbOfTxs(String.valueOf(ddd.getNumberOfPaymentsByMandateType(mandateType)));
        // control sum
        result.setCtrlSum(ddd.getTotalPaymentSumOfPaymentsByMandateType(mandateType));
        // payment type information
        result.setPmtTpInf(createPaymentTypeInformation(mandateType));

        // requested collection due date
        result.setReqdColltnDt(dateToXmlGregorianCalendarDate(ddd.getDueDateByMandateType(mandateType)));

        // creditor name
        result.setCdtr(new PartyIdentificationSEPA5());
        result.getCdtr().setNm(ddd.getCreditorName());

        // creditor iban
        result.setCdtrAcct(ibanToCashAccountSepa1(ddd.getCreditorIban()));

        // creditor agt(?)
        result.setCdtrAgt(bicToBranchAndFinancialInstitutionIdentification(ddd.getCreditorBic()));

        // whatever, fixed
        result.setChrgBr(ChargeBearerTypeSEPACode.SLEV);

        // single payment transactions ... yay!
        result.getDrctDbtTxInf().addAll(createDirectDebitTransactionInformationBlocks(ddd, mandateType));

        return result;
    }

    private static CashAccountSEPA1 ibanToCashAccountSepa1(String iban) {
        CashAccountSEPA1 result = new CashAccountSEPA1();
        result.setId(new AccountIdentificationSEPA());
        result.getId().setIBAN(iban);
        return result;
    }

    private static Collection<? extends DirectDebitTransactionInformationSDD> createDirectDebitTransactionInformationBlocks(DirectDebitDocumentData ddd, MandateType mandateType) throws DatatypeConfigurationException {
        List<DirectDebitTransactionInformationSDD> result = new ArrayList<>();

        for (DirectDebitPayment p : ddd.getPaymentsByMandateType(mandateType)) {
            result.add(createDirectDebitTransaction(ddd, p));
        }

        return result;
    }

    private static DirectDebitTransactionInformationSDD createDirectDebitTransaction(DirectDebitDocumentData ddd, DirectDebitPayment p) throws DatatypeConfigurationException {
        DirectDebitTransactionInformationSDD result = new DirectDebitTransactionInformationSDD();
        // mandate id
        result.setPmtId(new PaymentIdentificationSEPA());
        result.getPmtId().setEndToEndId(p.getMandateId());

        // currency and amount
        result.setInstdAmt(new ActiveOrHistoricCurrencyAndAmountSEPA());
        result.getInstdAmt().setCcy(ActiveOrHistoricCurrencyCodeEUR.EUR);
        result.getInstdAmt().setValue(p.getPaymentSum());

        // transaction information
        result.setDrctDbtTx(createDirectDebitTransaction(p, ddd));

        // debitor bic
        result.setDbtrAgt(bicToBranchAndFinancialInstitutionIdentification(p.getDebitorBic()));

        // debitor name
        result.setDbtr(new PartyIdentificationSEPA2());
        result.getDbtr().setNm(p.getDebitorName());

        // debitor iban
        result.setDbtrAcct(new CashAccountSEPA2());
        result.getDbtrAcct().setId(new AccountIdentificationSEPA());
        result.getDbtrAcct().getId().setIBAN(p.getDebitorIban());

        // reson of payment
        result.setRmtInf(new RemittanceInformationSEPA1Choice());
        result.getRmtInf().setUstrd(p.getReasonForPayment());

        return result;
    }

    private static DirectDebitTransactionSDD createDirectDebitTransaction(DirectDebitPayment p, DirectDebitDocumentData ddd) throws DatatypeConfigurationException {
        DirectDebitTransactionSDD result = new DirectDebitTransactionSDD();
        // mandate related info
        result.setMndtRltdInf(new MandateRelatedInformationSDD());

        // Erforderlich, wenn das Mandat seit letzten SEPA Lastschrift Einreichung ge�ndert wurde.
        // In diesem Fall ist das Feld mit "TRUE" zu belegen, ansonsten bleibt es leer.
        // Relevanz f�r folgende Mandats�nderungen: Gl�ubiger ID, Gl�ubiger Name, Bankverbindung des Zahlungspflichtigen, Mandat ID
        // -- we'll leave it empty for now and see what happens
        // tx.getMndtRltdInf().setAmdmntInd(Boolean.FALSE);
        result.getMndtRltdInf().setMndtId(p.getMandateId());
        result.getMndtRltdInf().setDtOfSgntr(dateToXmlGregorianCalendarDate(p.getMandateDate()));

        // creditor related info
        result.setCdtrSchmeId(new PartyIdentificationSEPA3());
        result.getCdtrSchmeId().setId(new PartySEPA2());
        result.getCdtrSchmeId().getId().setPrvtId(new PersonIdentificationSEPA2());

        // person identification - (creditor identifier)
        RestrictedPersonIdentificationSEPA inf = new RestrictedPersonIdentificationSEPA();
        result.getCdtrSchmeId().getId().getPrvtId().setOthr(inf);
        inf.setId(ddd.getCreditorIdentifier());

        // whatever, fixed to SEPA
        inf.setSchmeNm(new RestrictedPersonIdentificationSchemeNameSEPA());
        inf.getSchmeNm().setPrtry(IdentificationSchemeNameSEPA.SEPA);

        return result;
    }

    private static PaymentTypeInformationSDD createPaymentTypeInformation(MandateType mandateType) {
        PaymentTypeInformationSDD paymentType = new PaymentTypeInformationSDD();
        paymentType.setSvcLvl(new ServiceLevelSEPA());
        paymentType.getSvcLvl().setCd("SEPA");
        paymentType.setLclInstrm(new LocalInstrumentSEPA());
        paymentType.getLclInstrm().setCd("CORE");
        paymentType.setSeqTp(mandateType.getSepaSequenceType1Code());
        return paymentType;
    }

    private static GroupHeaderSDD createGroupHeaderSdd(DirectDebitDocumentData ddd) throws DatatypeConfigurationException {
        GroupHeaderSDD result = new GroupHeaderSDD();
        // message id
        result.setMsgId(ddd.getDocumentMessageId());

        // created on
        result.setCreDtTm(calendarToXmlGregorianCalendarDateTime((GregorianCalendar) GregorianCalendar.getInstance()));

        // number of tx
        result.setNbOfTxs(String.valueOf(ddd.getPayments().size()));

        // control sum
        result.setCtrlSum(ddd.getTotalPaymentSum());

        // creditor name
        PartyIdentificationSEPA1 partyIdentificationSEPA1 = new PartyIdentificationSEPA1();
        partyIdentificationSEPA1.setNm(ddd.getCreditorName());

        result.setInitgPty(partyIdentificationSEPA1);

        return result;
    }

}
