package com.ibm.streamsx.messagehub.test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.ibm.streamsx.messagehub.test.utils.Constants;
import com.ibm.streamsx.messagehub.test.utils.Delay;
import com.ibm.streamsx.messagehub.test.utils.MessageHubSPLStreamsUtils;
import com.ibm.streamsx.topology.TStream;
import com.ibm.streamsx.topology.Topology;
import com.ibm.streamsx.topology.context.StreamsContext;
import com.ibm.streamsx.topology.context.StreamsContext.Type;
import com.ibm.streamsx.topology.context.StreamsContextFactory;
import com.ibm.streamsx.topology.spl.SPL;
import com.ibm.streamsx.topology.spl.SPLStream;
import com.ibm.streamsx.topology.spl.SPLStreams;
import com.ibm.streamsx.topology.tester.Condition;
import com.ibm.streamsx.topology.tester.Tester;

public class MessageHubAppConfigParamTest extends AbstractMessageHubTest {

	private static final String TEST_NAME = "MessageHubAppConfigParamTest";
	private static final String APP_CONFIG_NAME = "userAppConfig";
	
	public MessageHubAppConfigParamTest() throws Exception {
		super(TEST_NAME);
	}
	
	@Before
	public void setup() throws Exception {
		String creds = new String(Files.readAllBytes(Paths.get("etc/messagehub.json")));
		creds = "messagehub.creds=" + creds.replace("=", "&#61;");
		
		ProcessBuilder pb = new ProcessBuilder(System.getenv("STREAMS_INSTALL") + "/bin/streamtool", "mkappconfig", "--property", creds, APP_CONFIG_NAME);
		pb.inheritIO();
		Process p = pb.start();
		
		p.waitFor(25, TimeUnit.SECONDS);
		if(p.exitValue() != 0) {
			System.out.println(p.exitValue());
			Assert.fail("Creating app config failed! Test cancelled!");
		}
	}
	
	@After
	public void cleanup() throws Exception {
		ProcessBuilder pb = new ProcessBuilder(System.getenv("STREAMS_INSTALL") + "/bin/streamtool", "rmappconfig", "--noprompt", APP_CONFIG_NAME);
		pb.inheritIO();
		Process p = pb.start();
		
		p.waitFor(25, TimeUnit.SECONDS);
		if(p.exitValue() != 0) {
			System.out.println(p.exitValue());
			Assert.fail("Removing appconfig failed. App config must be removed manually!");
		}
	}
	
	@Test
	public void messageHubAppConfigParamTest() throws Exception {
		Topology topo = getTopology();
		
		// create the producer (produces tuples after a short delay)
		TStream<String> stringSrcStream = topo.strings(Constants.STRING_DATA).modify(new Delay<>(5000));
		SPL.invokeSink(com.ibm.streamsx.messagehub.test.utils.Constants.MessageHubProducerOp, 
				MessageHubSPLStreamsUtils.convertStreamToMessageHubTupleTuple(stringSrcStream), 
				getKafkaParams());

		// create the consumer
		SPLStream consumerStream = SPL.invokeSource(topo, Constants.MessageHubConsumerOp, getKafkaParams(), MessageHubSPLStreamsUtils.STRING_SCHEMA);
		SPLStream msgStream = SPLStreams.stringToSPLStream(consumerStream.convert(t -> t.getString("message")));
		
		// test the output of the consumer
		StreamsContext<?> context = StreamsContextFactory.getStreamsContext(Type.DISTRIBUTED_TESTER);
		Tester tester = topo.getTester();
		Condition<List<String>> condition = MessageHubSPLStreamsUtils.stringContentsUnordered(tester, msgStream, Constants.STRING_DATA);
		tester.complete(context, new HashMap<>(), condition, 30, TimeUnit.SECONDS);

		// check the results
		Assert.assertTrue(condition.getResult().size() > 0);
		Assert.assertTrue(condition.getResult().toString(), condition.valid());		
	}
	
	private Map<String, Object> getKafkaParams() {
		Map<String, Object> params = new HashMap<String, Object>();
		
		params.put("topic", Constants.TOPIC_TEST);
		params.put("appConfigName", APP_CONFIG_NAME);
		
		return params;
	}
}
	
