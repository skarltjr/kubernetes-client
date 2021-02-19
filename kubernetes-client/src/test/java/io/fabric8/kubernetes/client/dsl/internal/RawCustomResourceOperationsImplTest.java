/**
 * Copyright (C) 2015 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.kubernetes.client.dsl.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.DeleteOptionsBuilder;
import io.fabric8.kubernetes.api.model.DeletionPropagation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import io.fabric8.kubernetes.api.model.ListOptionsBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.utils.Utils;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.assertj.core.api.Assertions.assertThat;

public class RawCustomResourceOperationsImplTest {
  private OkHttpClient mockClient;
  private Config config;
  private CustomResourceDefinitionContext customResourceDefinitionContext;
  private Response mockSuccessResponse;

  @BeforeEach
  public void setUp() throws IOException {
    this.mockClient = Mockito.mock(OkHttpClient.class, Mockito.RETURNS_DEEP_STUBS);
    this.config = new ConfigBuilder().withMasterUrl("https://localhost:8443/").build();
    this.customResourceDefinitionContext = new CustomResourceDefinitionContext.Builder()
      .withGroup("test.fabric8.io")
      .withName("hellos.test.fabric8.io")
      .withPlural("hellos")
      .withScope("Namespaced")
      .withVersion("v1alpha1")
      .build();

    Call mockCall = mock(Call.class);
    mockSuccessResponse = mockResponse(HttpURLConnection.HTTP_OK);
    when(mockCall.execute())
      .thenReturn(mockSuccessResponse);
    when(mockClient.newCall(any())).thenReturn(mockCall);
  }

  @Test
  void testCreateOrReplaceUrl() throws IOException {
    // Given
    RawCustomResourceOperationsImpl rawCustomResourceOperations = new RawCustomResourceOperationsImpl(mockClient, config, customResourceDefinitionContext);
    String resourceAsString = "{\"metadata\":{\"name\":\"myresource\",\"namespace\":\"myns\"}, \"kind\":\"raw\", \"apiVersion\":\"v1\"}";
    ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);

    Call mockCall = mock(Call.class);
    Response mockErrorResponse = mockResponse(HttpURLConnection.HTTP_INTERNAL_ERROR);
    Response mockConflictResponse = mockResponse(HttpURLConnection.HTTP_CONFLICT);
    when(mockCall.execute())
      .thenReturn(mockErrorResponse, mockConflictResponse, mockSuccessResponse);
    when(mockClient.newCall(any())).thenReturn(mockCall);

    // When
    try {
      rawCustomResourceOperations.createOrReplace(resourceAsString);
      fail("expected first call to createOrReplace to throw exception due to 500 response");
    } catch (KubernetesClientException e) {
      assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, e.getCode());
    }
    rawCustomResourceOperations.createOrReplace(resourceAsString);
    rawCustomResourceOperations.createOrReplace("myns", resourceAsString);

    // Then
    verify(mockClient, times(4)).newCall(captor.capture());
    assertEquals(4, captor.getAllValues().size());
    assertEquals("/apis/test.fabric8.io/v1alpha1/hellos", captor.getAllValues().get(0).url().encodedPath());
    assertEquals("POST", captor.getAllValues().get(0).method());
    assertEquals("/apis/test.fabric8.io/v1alpha1/hellos", captor.getAllValues().get(1).url().encodedPath());
    assertEquals("POST", captor.getAllValues().get(1).method());
    assertEquals("/apis/test.fabric8.io/v1alpha1/hellos/myresource", captor.getAllValues().get(2).url().encodedPath());
    assertEquals("PUT", captor.getAllValues().get(2).method());
    assertEquals("/apis/test.fabric8.io/v1alpha1/namespaces/myns/hellos", captor.getAllValues().get(3).url().encodedPath());
    assertEquals("POST", captor.getAllValues().get(3).method());
  }

  @Test
  void testDeleteWithNamespaceAndNameForNonExistentResource() throws IOException {
    // Given
    RawCustomResourceOperationsImpl rawCustomResourceOperations = new RawCustomResourceOperationsImpl(mockClient, config, customResourceDefinitionContext);
    mockDeletionCallWithResponse(HttpURLConnection.HTTP_NOT_FOUND, "{\"kind\":\"Status\",\"status\":\"Failure\"}");
    ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);

    // When
    boolean result = rawCustomResourceOperations.delete("ns1", "foo");

    // Then
    assertFalse(result);
    verify(mockClient).newCall(captor.capture());
    assertEquals("/apis/test.fabric8.io/v1alpha1/namespaces/ns1/hellos/foo", captor.getValue().url().encodedPath());
  }

  @Test
  void testDeleteWithNamespaceForNonExistentResource() throws IOException {
    // Given
    RawCustomResourceOperationsImpl rawCustomResourceOperations = new RawCustomResourceOperationsImpl(mockClient, config, customResourceDefinitionContext);
    mockDeletionCallWithResponse(HttpURLConnection.HTTP_NOT_FOUND, "{\"kind\":\"Status\",\"status\":\"Failure\"}");
    ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);

    // When
    boolean result = rawCustomResourceOperations.delete("ns1");

    // Then
    assertFalse(result);
    verify(mockClient).newCall(captor.capture());
    assertEquals("/apis/test.fabric8.io/v1alpha1/namespaces/ns1/hellos", captor.getValue().url().encodedPath());
  }

  @Test
  void testDeleteWithNamespaceAndCascadingForNonExistentResource() throws IOException {
    // Given
    RawCustomResourceOperationsImpl rawCustomResourceOperations = new RawCustomResourceOperationsImpl(mockClient, config, customResourceDefinitionContext);
    mockDeletionCallWithResponse(HttpURLConnection.HTTP_NOT_FOUND, "{\"kind\":\"Status\",\"status\":\"Failure\"}");
    ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);

    // When
    boolean result = rawCustomResourceOperations.delete("ns1", true);

    // Then
    assertFalse(result);
    verify(mockClient).newCall(captor.capture());
    assertEquals("/apis/test.fabric8.io/v1alpha1/namespaces/ns1/hellos", captor.getValue().url().encodedPath());
  }

  @Test
  void testDeleteWithNamespaceAndDeleteOptionsForNonExistentResource() throws IOException {
    // Given
    RawCustomResourceOperationsImpl rawCustomResourceOperations = new RawCustomResourceOperationsImpl(mockClient, config, customResourceDefinitionContext);
    mockDeletionCallWithResponse(HttpURLConnection.HTTP_NOT_FOUND, "{\"kind\":\"Status\",\"status\":\"Failure\"}");
    ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);

    // When
    boolean result = rawCustomResourceOperations.delete("ns1", new DeleteOptionsBuilder().withOrphanDependents(true).build());

    // Then
    assertFalse(result);
    verify(mockClient).newCall(captor.capture());
    assertEquals("/apis/test.fabric8.io/v1alpha1/namespaces/ns1/hellos", captor.getValue().url().encodedPath());
  }

  @Test
  void testDeleteWithNamespaceNameAndCascading() throws IOException {
    // Given
    RawCustomResourceOperationsImpl rawCustomResourceOperations = new RawCustomResourceOperationsImpl(mockClient, config, customResourceDefinitionContext);
    mockDeletionCallWithResponse(HttpURLConnection.HTTP_OK, "{\"kind\":\"Hello\",\"metadata\":{\"name\":\"Failure\"}}");
    ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);

    // When
    boolean result = rawCustomResourceOperations.delete("ns1", "foo", true);

    // Then
    assertTrue(result);
    verify(mockClient).newCall(captor.capture());
    assertEquals("/apis/test.fabric8.io/v1alpha1/namespaces/ns1/hellos/foo", captor.getValue().url().encodedPath());
  }

  @Test
  void testDeleteWithNamespaceNameAndCascadingForNonExistentResource() throws IOException {
    // Given
    RawCustomResourceOperationsImpl rawCustomResourceOperations = new RawCustomResourceOperationsImpl(mockClient, config, customResourceDefinitionContext);
    mockDeletionCallWithResponse(HttpURLConnection.HTTP_NOT_FOUND, "{\"kind\":\"Status\",\"status\":\"Failure\"}");
    ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);

    // When
    boolean result = rawCustomResourceOperations.delete("ns1", "foo", true);

    // Then
    assertFalse(result);
    verify(mockClient).newCall(captor.capture());
    assertEquals("/apis/test.fabric8.io/v1alpha1/namespaces/ns1/hellos/foo", captor.getValue().url().encodedPath());
  }

  @Test
  void testDeleteWithNamespaceNameAndPropagationPolicyForNonExistentResource() throws IOException {
    // Given
    RawCustomResourceOperationsImpl rawCustomResourceOperations = new RawCustomResourceOperationsImpl(mockClient, config, customResourceDefinitionContext);
    mockDeletionCallWithResponse(HttpURLConnection.HTTP_NOT_FOUND, "{\"kind\":\"Status\",\"status\":\"Failure\"}");
    ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);

    // When
    boolean result = rawCustomResourceOperations.delete("ns1", "foo", DeletionPropagation.BACKGROUND.toString());

    // Then
    assertFalse(result);
    verify(mockClient).newCall(captor.capture());
    assertEquals("/apis/test.fabric8.io/v1alpha1/namespaces/ns1/hellos/foo", captor.getValue().url().encodedPath());
  }

  @Test
  void testDeleteWithNamespaceNameAndDeleteOptionsForNonExistentResource() throws IOException {
    // Given
    RawCustomResourceOperationsImpl rawCustomResourceOperations = new RawCustomResourceOperationsImpl(mockClient, config, customResourceDefinitionContext);
    mockDeletionCallWithResponse(HttpURLConnection.HTTP_NOT_FOUND, "{\"kind\":\"Status\",\"status\":\"Failure\"}");
    ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);

    // When
    boolean result = rawCustomResourceOperations.delete("ns1", "foo", new DeleteOptionsBuilder().withPropagationPolicy(DeletionPropagation.FOREGROUND.toString()).build());

    // Then
    assertFalse(result);
    verify(mockClient).newCall(captor.capture());
    assertEquals("/apis/test.fabric8.io/v1alpha1/namespaces/ns1/hellos/foo", captor.getValue().url().encodedPath());
  }

  @Test
  void testGetUrl() {
    // Given
    RawCustomResourceOperationsImpl rawCustomResourceOperations = new RawCustomResourceOperationsImpl(mockClient, config, customResourceDefinitionContext);
    ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);

    // When
    rawCustomResourceOperations.get("myns", "myresource");

    // Then
    verify(mockClient).newCall(captor.capture());
    assertEquals("/apis/test.fabric8.io/v1alpha1/namespaces/myns/hellos/myresource", captor.getValue().url().encodedPath());
  }

  @Test
  void testDeleteUrl() throws IOException {
    // Given
    RawCustomResourceOperationsImpl rawCustomResourceOperations = new RawCustomResourceOperationsImpl(mockClient, config, customResourceDefinitionContext);
    ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
    mockDeletionCallWithResponse(HttpURLConnection.HTTP_OK, "{\"kind\":\"Status\",\"status\":\"Success\"}");

    // When
    boolean result = rawCustomResourceOperations.delete("myns", "myresource");

    // Then
    assertTrue(result);
    verify(mockClient).newCall(captor.capture());
    assertEquals("/apis/test.fabric8.io/v1alpha1/namespaces/myns/hellos/myresource", captor.getValue().url().encodedPath());
  }

  @Test
  void testFetchWatchUrlWithNamespace() throws MalformedURLException {
    // Given
    RawCustomResourceOperationsImpl rawCustomResourceOperations = new RawCustomResourceOperationsImpl(mockClient, config, customResourceDefinitionContext);

    // When
    HttpUrl url = rawCustomResourceOperations.fetchWatchUrl("test", null, null, new ListOptionsBuilder().withWatch(true).build()).build();

    // Then
    assertEquals("https://localhost:8443/apis/test.fabric8.io/v1alpha1/namespaces/test/hellos?watch=true", url.url().toString());
  }

  @Test
  void testFetchWatchUrlWithNamespaceAndName() throws MalformedURLException {
    // Given
    RawCustomResourceOperationsImpl rawCustomResourceOperations = new RawCustomResourceOperationsImpl(mockClient, config, customResourceDefinitionContext);

    // When
    HttpUrl url = rawCustomResourceOperations.fetchWatchUrl("test", "example-resource", null, new ListOptionsBuilder().withWatch(true).build()).build();

    // Then
    assertEquals("https://localhost:8443/apis/test.fabric8.io/v1alpha1/namespaces/test/hellos?fieldSelector=metadata.name%3Dexample-resource&watch=true", url.url().toString());
  }

  @Test
  void testFetchWatchUrlWithNamespaceAndNameAndResourceVersion() throws MalformedURLException {
    // Given
    RawCustomResourceOperationsImpl rawCustomResourceOperations = new RawCustomResourceOperationsImpl(mockClient, config, customResourceDefinitionContext);

    // When
    HttpUrl url = rawCustomResourceOperations.fetchWatchUrl("test", "example-resource", null, new ListOptionsBuilder().withResourceVersion("100069").withWatch(true).build()).build();

    // Then
    assertEquals("https://localhost:8443/apis/test.fabric8.io/v1alpha1/namespaces/test/hellos?fieldSelector=metadata.name%3Dexample-resource&resourceVersion=100069&watch=true", url.url().toString());
  }

  @Test
  void testFetchWatchUrlWithoutAnything() throws MalformedURLException {
    // Given
    RawCustomResourceOperationsImpl rawCustomResourceOperations = new RawCustomResourceOperationsImpl(mockClient, config, customResourceDefinitionContext);

    // When
    HttpUrl url = rawCustomResourceOperations.fetchWatchUrl(null, null, null, new ListOptionsBuilder().withWatch(true).build()).build();

    // Then
    assertEquals("https://localhost:8443/apis/test.fabric8.io/v1alpha1/hellos?watch=true", url.url().toString());
  }

  @Test
  void testFetchWatchUrlWithLabels() throws MalformedURLException {
    // Given
    RawCustomResourceOperationsImpl rawCustomResourceOperations = new RawCustomResourceOperationsImpl(mockClient, config, customResourceDefinitionContext);

    // When
    Map<String, String> labels = new HashMap<>();
    labels.put("foo", "bar");
    labels.put("foo1", "bar1");

    HttpUrl url = rawCustomResourceOperations.fetchWatchUrl(null, null, labels, new ListOptionsBuilder().withWatch(true).build()).build();

    // Then
    assertEquals("https://localhost:8443/apis/test.fabric8.io/v1alpha1/hellos?labelSelector=" + Utils.toUrlEncoded("foo=bar") + "," + Utils.toUrlEncoded("foo1=bar1") + "&watch=true", url.url().toString());
  }

  @Test
  void testFetchWatchUrlWithLabelsWithNamespace() throws MalformedURLException {
    // Given
    RawCustomResourceOperationsImpl rawCustomResourceOperations = new RawCustomResourceOperationsImpl(mockClient, config, customResourceDefinitionContext);

    // When
    Map<String, String> labels = new HashMap<>();
    labels.put("foo", "bar");
    labels.put("foo1", "bar1");

    HttpUrl url = rawCustomResourceOperations.fetchWatchUrl("test", null, labels, new ListOptionsBuilder().withWatch(true).build()).build();

    // Then
    assertEquals("https://localhost:8443/apis/test.fabric8.io/v1alpha1/namespaces/test/hellos?labelSelector=" + Utils.toUrlEncoded("foo=bar") + "," + Utils.toUrlEncoded("foo1=bar1") + "&watch=true", url.url().toString());
  }

  @Test
  void testGetConfigShouldNotReturnNull() {
    // Given
    Config config = new ConfigBuilder()
      .withRequestTimeout(5)
      .withWebsocketTimeout(10L)
      .withWebsocketPingInterval(10L)
      .withConnectionTimeout(10)
      .withWatchReconnectLimit(1)
      .withWatchReconnectInterval(10)
      .build();
    RawCustomResourceOperationsImpl rawOp = new RawCustomResourceOperationsImpl(mockClient, config, customResourceDefinitionContext);

    // When
    Config configFromRawOp = rawOp.getConfig();

    // Then
    assertThat(configFromRawOp).isNotNull();
    assertThat(configFromRawOp.getRequestTimeout()).isEqualTo(5);
    assertThat(configFromRawOp.getWebsocketTimeout()).isEqualTo(10L);
    assertThat(configFromRawOp.getWebsocketPingInterval()).isEqualTo(10L);
    assertThat(configFromRawOp.getConnectionTimeout()).isEqualTo(10L);
    assertThat(configFromRawOp.getWatchReconnectInterval()).isEqualTo(10);
    assertThat(configFromRawOp.getWatchReconnectLimit()).isEqualTo(1);
  }

  private void mockDeletionCallWithResponse(int code, String status) throws IOException {
    Call mockCall = mock(Call.class);
    Response mockNotFoundResponse = mockResponse(code, status);
    when(mockCall.execute())
      .thenReturn(mockNotFoundResponse);
    when(mockClient.newCall(any())).thenReturn(mockCall);
  }

  private Response mockResponse(int code) {
    return mockResponse(code, "");
  }

  private Response mockResponse(int code, String body) {
    return new Response.Builder()
      .request(new Request.Builder().url("http://mock").build())
      .protocol(Protocol.HTTP_1_1)
      .code(code)
      .body(ResponseBody.create(MediaType.get("application/json"), body))
      .message("mock")
      .build();
  }

}
