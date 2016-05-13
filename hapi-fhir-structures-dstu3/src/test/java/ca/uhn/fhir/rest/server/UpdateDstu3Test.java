package ca.uhn.fhir.rest.server;

import static org.junit.Assert.*;

import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.OperationOutcome;
import org.hl7.fhir.dstu3.model.Patient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.util.PortUtil;
import ca.uhn.fhir.util.TestUtil;

public class UpdateDstu3Test {
	private static CloseableHttpClient ourClient;
	private static FhirContext ourCtx = FhirContext.forDstu3();
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(UpdateDstu3Test.class);
	private static int ourPort;
	private static Server ourServer;

	@Test
	public void testUpdateMissingUrlInBody() throws Exception {

		Patient patient = new Patient();
		patient.addIdentifier().setValue("002");

		HttpPut httpPost = new HttpPut("http://localhost:" + ourPort + "/Patient/001");
		httpPost.setEntity(new StringEntity(ourCtx.newXmlParser().encodeResourceToString(patient), ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));

		HttpResponse status = ourClient.execute(httpPost);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());

		ourLog.info("Response was:\n{}", responseContent);

		OperationOutcome oo = ourCtx.newXmlParser().parseResource(OperationOutcome.class, responseContent);
		assertEquals("OODETAILS", oo.getIssueFirstRep().getDiagnostics());

		assertEquals(200, status.getStatusLine().getStatusCode());
		assertEquals("http://localhost:" + ourPort + "/Patient/001/_history/002", status.getFirstHeader("location").getValue());
		assertEquals("http://localhost:" + ourPort + "/Patient/001/_history/002", status.getFirstHeader("content-location").getValue());

	}

	@Test
	public void testUpdateWrongUrlInBody() throws Exception {

		Patient patient = new Patient();
		patient.setId("3");
		patient.addIdentifier().setValue("002");

		HttpPut httpPost = new HttpPut("http://localhost:" + ourPort + "/Patient/001");
		httpPost.setEntity(new StringEntity(ourCtx.newXmlParser().encodeResourceToString(patient), ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));

		HttpResponse status = ourClient.execute(httpPost);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());

		ourLog.info("Response was:\n{}", responseContent);

		OperationOutcome oo = ourCtx.newXmlParser().parseResource(OperationOutcome.class, responseContent);
		assertEquals("Can not update resource, resource body must contain an ID element which matches the request URL for update (PUT) operation - Resource body ID of \"3\" does not match URL ID of \"001\"", oo.getIssueFirstRep().getDiagnostics());

		assertEquals(400, status.getStatusLine().getStatusCode());
		assertNull(status.getFirstHeader("location"));
		assertNull(status.getFirstHeader("content-location"));

	}

	@AfterClass
	public static void afterClassClearContext() throws Exception {
		ourServer.stop();
		TestUtil.clearAllStaticFieldsForUnitTest();
	}

	@BeforeClass
	public static void beforeClass() throws Exception {
		ourPort = PortUtil.findFreePort();
		ourServer = new Server(ourPort);

		ServletHandler proxyHandler = new ServletHandler();
		RestfulServer servlet = new RestfulServer(ourCtx);
		servlet.setResourceProviders(new PatientProvider());
		ServletHolder servletHolder = new ServletHolder(servlet);
		proxyHandler.addServletWithMapping(servletHolder, "/*");
		ourServer.setHandler(proxyHandler);
		ourServer.start();

		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(5000, TimeUnit.MILLISECONDS);
		HttpClientBuilder builder = HttpClientBuilder.create();
		builder.setConnectionManager(connectionManager);
		ourClient = builder.build();

	}



	public static class PatientProvider implements IResourceProvider {

		@Override
		public Class<Patient> getResourceType() {
			return Patient.class;
		}

		@Update()
		public MethodOutcome updatePatient(@IdParam IdType theId, @ResourceParam Patient thePatient) {
			IdType id = theId.withVersion(thePatient.getIdentifierFirstRep().getValue());
			OperationOutcome oo = new OperationOutcome();
			oo.addIssue().setDiagnostics("OODETAILS");
			if (theId.getValueAsString().contains("CREATE")) {
				return new MethodOutcome(id, oo, true);
			}

			return new MethodOutcome(id, oo);
		}

	}
}
