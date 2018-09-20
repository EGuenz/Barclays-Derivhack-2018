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

import javax.json.Json;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperledger.fabric.shim.ChaincodeBase;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.isda.cdm.*;
import com.google.gson.*;

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

		//TODO: Unmarshall tradeJson into a CDM contract, make composite key based on Contract Fields
    Gson gson = new Gson();
		Contract contract = gson.fromJson(tradeJson, Contract.class);
		String id = contract.getId();
		String key = stub.createCompositeKey("initialTrade", id)
		stub.putStringState(key, tradeJson);
		return newSuccessResponse();
	}


	private Response init(ChaincodeStub stub, String[] args) {
		if (args.length != 1) throw new IllegalArgumentException("Incorrect number of arguments. Expecting: init(accountData)");

		final String accountsJson = args[0];
		stub.putStringState("accounts", accountsJson);

		return newSuccessResponse();
	}

/*

	private Response delete(ChaincodeStub stub, String args[]) {
		if (args.length != 1) throw new IllegalArgumentException("Incorrect number of arguments. Expecting: delete(account)");

		final String account = args[0];

		stub.delState(account);

		return newSuccessResponse();
	}

	*/

	private Response query(ChaincodeStub stub, String[] args) {
		if (args.length != 1) throw new IllegalArgumentException("Incorrect number of arguments. Expecting: query(Id)");

		final String id = args[0];
		String key = stub.createCompositeKey("initialTrade", id);

		return newSuccessResponse(Json.createObjectBuilder()
				.add("Trade", key)
				.add("Content", stub.getStringState(key))
				.build().toString().getBytes(UTF_8));
	}

	public static void main(String[] args) throws Exception {
		new Cdm().start(args);
	}

}
