/*
Copyright DTCC, IBM 2016, 2017 All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/


//Event, IntentEnum, Event.EventBuilder, Event.EventEffectBuilder, payment, contract,
package example;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.List;
import java.util.ArrayList;
import javax.json.Json;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperledger.fabric.shim.ChaincodeBase;
import org.hyperledger.fabric.shim.ChaincodeStub;

import org.isda.cdm.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper;

public class Cdm extends ChaincodeBase {

	private static Log log = LogFactory.getLog(Cdm.class);

	@Override
	public Response init(ChaincodeStub stub) {
		try {
			final String function = stub.getFunction();
			switch (function) {
			case "init":
				return init(stub, stub.getParameters().stream().toArray(String[]::new));
			default:
				return newErrorResponse(format("Unknown function: %s", function));
			}
		} catch (Throwable e) {
			return newErrorResponse(e);
		}
	}

@Override
public Response invoke(ChaincodeStub stub) {

	try {
		final String function = stub.getFunction();
		final String[] args = stub.getParameters().stream().toArray(String[]::new);

		switch (function) {
		case "submit":
			return move(stub, args);
		case "query":
			return query(stub, args);
		default:
			return newErrorResponse(format("Unknown function: %s", function));
		}
	} catch (Throwable e) {
		return newErrorResponse(e);
	}

}

  private Response submit(ChaincodeStub stub, String[] args){
  	if (args.length != 1) throw new IllegalArgumentException("Incorrect number of arguments. Expecting: submit(trade)");
  	final String tradeJson = args[0];
  	//Unmarshall tradeJson into a CDM Event
  	ObjectMapper rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper();
  	Event event = rosettaObjectMapper.readValue(json, event.getClass());
  	return processEvent(stub, event);
  }

  private Response processEvent(Chaincode stub, Event event){
  	IntentEnum eventType = event.getIntent();
		String id = event.getEventIdentifier();
		String date = event.getEventDate().toString();
		String key = stub.createCompositeKey("tradeEvent", date, id);
  	if (eventType == NEW_TRADE || eventType == TERMINATION || eventType == PARTIAL_TERMINATION){
			 String eventString = event.toString();
		   stub.putStringState(key, eventString);
		} else if (eventType == NOVATION || eventType == PARTIAL_NOVATION){
			 processNovation(stub, event, key)
		}
  	return newSuccessResponse();
  }

  //creates copy of original trade with event specifics deleted, writes to ledger
	private Event clearEffects(Event e){
		return (Event.EventBuilder.clone(event.toBuilder())
											.setEventEffect(EventEffectBuilder.build())
													.build());
	}

	private void processNovation(Chaincode stub, Event event, String key){

		Event eNew;

    //Initializes New Contract created, Contract Modified and payment info for original Event
		List<Payment> newPayments = event.getPrimitive().getPayment();

		Contract created = event.getPrimitive().getNewTrade().get(0).getContract();
		Contract modified = event.getPrimitive().getTermsChange().getAfter().getContract().get(0);


    //Find payer-receiver match for created, create new Contract, add created to private channel
		eNew = matchPaymentAndBuild(event, created, payments);
		String createdString = eNew.toString();
		stub.putStringState(key, createdString);


   // Find payer-receiver match for modified contract, create New, remove payer from add created to Hyperledger
		eNew = matchPaymentAndBuild(event, modified, payments);
		String modifiedString = eNew.toString();
		stub.putStringState(key, modifiedString);


		int numPaymentsLeft = newPayments.size();
		//Deal with left over payments
		for (j = 0; j < numPaymentsLeft; j++){
				eNew = clearEffects(event).setEffectedEvent(EventEffect.builder().
									 addPayment(pay.toString()).
											 build());
					stub.putStringState(key, eNew.toString());
		}

	}

	private Event matchPaymentAndBuild(Event event, Contract contract, List<Payment> payments){
    Payment pay;
		Event eNew;

		List<String> partyIds = created.getParty().stream()
																	.map(p -> p.getPartyIdScheme());
		for (int i = 0; i < payments.size(); i++){
			  pay = payments.get(i);
			  if contractParties.contains(pay.getPayerReceiver.getPayerPartyReference()) &&
				   contractParties.contains(pay.getPayerReceiver.getReceiverPartyReference()){
						 eNew = clearEffects(event).setEffectedEvent(EventEffect.builder().
						 						addPayment(pay.toString()).
														build());
						 payments.remove(i);
						 break;
					 }
		}
		eNew = eNew.setEffectedEvent(eNew.getEventEffect().toBuilder().
		             addContract(contract.toString()));

		return eNew;
	}

	private Contract parseContract(String contractString){
    ObjectMapper rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper();
		Contract contract = rosettaObjectMapper.readValue(json, contract.getClass());
		return contract;
	}

	private Payment parsePayment(String paymentString){
		ObjectMapper rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper();
		Payment payment = rosettaObjectMapper.readValue(json, payment.getClass());
		return payment;
	}

	private Response init(ChaincodeStub stub, String[] args) {
		if (args.length != 1) throw new IllegalArgumentException("Incorrect number of arguments. Expecting: init(accountData)");

		final String accountsJson = args[0];
		stub.putStringState("accounts", accountsJson);

		return newSuccessResponse();
	}

	private Response query(ChaincodeStub stub, String[] args) {
		if (args.length > 3 || args.length < 2) throw new IllegalArgumentException("Incorrect number of arguments. Expecting: query(history, date) or query(history, date, id)");

		final boolean history = (args[0] == "y");
		final String date = args[1];

		if (args.length == 3){
		  final String id = args[2];
			String key = stub.createCompositeKey("tradeEvent", date, id);
		  } //else{
			// String partialKey = stub.createCompositeKey("tradeEvent", date);
			// QueryResultsIterator<KeyValue> results = getStateByPartialCompositeKey(partialKey);
			// JsonObject object = Json.createObjectBuilder().build();
      // while(results.hasNext()){
			//
			// }

			return newSuccessResponse(Json.createObjectBuilder()
					.add("TradeEvent", key)
					.add("Content", stub.getStringState(key))
					.build().toString().getBytes(UTF_8));

	}

	public static void main(String[] args) throws Exception {
		new Cdm().start(args);
	}

}
