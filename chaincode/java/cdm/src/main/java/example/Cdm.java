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

package example;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import javax.json.Json;

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
  	return processTrade(stub, event);
  }

  private Response processTrade(Chaincode stub, Event event){
  	IntentEnum eventType = event.getIntent();
		String id = event.getEventIdentifier();
		String key = stub.createCompositeKey("tradeEvent", id);
  	if (eventType == NEW_TRADE || eventType == TERMINATION || eventType == PARTIAL_TERMINATION){
			 String eventString = event.toString();
		   stub.putStringState(key, eventString);
		} else if (eventType == NOVATION || eventType == PARTIAL_NOVATION){
			 processNovation(stub, event, key)
		}
		//TODO: Add other eventTypes for other Use Cases
  	return newSuccessResponse();
  }

	private void processNovation(Chaincode stub, Event event, String key){

		//creates copy of original trade with event specifics deleted, writes to ledger
		Event eNew = Event.EventBuilder.clone(event.toBuilder())
											.setEventEffect(EventEffectBuilder.build())
													.build();
    String eventString = eNew.toString();
		stub.putStringState(key, eventString);

    //Initializes contract and payment info for original trade
		List<String> newContracts = event.getEventEffect().getContract()
		List<String> newPayments = event.getEventEffect().getPayment();
		int i, j;
		String contractString, paymentString;
    Contract contract;
		Payment payment;

		//Matches contract to optional payment, constructs new events written to private channels
		//UNFINISHED, DO IN MORNING
		for (i = 0; i < newContracts.size(); i++){
			contractString = newContracts.get(i);
			contract = parseContract(contractString);
			for (j = 0; j < newPayments.size(); j++){
				paymentString = newPayments.get(j);
				payment = parsePayment(paymentString);
				String paymentPayer = payment.getPayerReceiver().getPayerPartyReference();
				String paymentReceiver = payment.getPayerReceiver().getReceiverPartyReference();
				String contractPayer =
				if (equals(contract.getParty()))
				//eventBuilder.addPayment(payment);
			}
			eNew = eNew.setEventEffectBuilder(eventBuilder);
		}
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
		if (args.length != 1) throw new IllegalArgumentException("Incorrect number of arguments. Expecting: query(Id)");

		final String id = args[0];
		String key = stub.createCompositeKey("tradeEvent", id);

		return newSuccessResponse(Json.createObjectBuilder()
				.add("TradeEvent", key)
				.add("Content", stub.getStringState(key))
				.build().toString().getBytes(UTF_8));
	}

	public static void main(String[] args) throws Exception {
		new Cdm().start(args);
	}

}
