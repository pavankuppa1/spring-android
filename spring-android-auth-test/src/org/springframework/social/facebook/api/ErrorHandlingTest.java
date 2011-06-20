/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.social.facebook.api;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.social.test.client.RequestMatchers.body;
import static org.springframework.social.test.client.RequestMatchers.header;
import static org.springframework.social.test.client.RequestMatchers.method;
import static org.springframework.social.test.client.RequestMatchers.requestTo;
import static org.springframework.social.test.client.ResponseCreators.withResponse;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.social.ExpiredAuthorizationException;
import org.springframework.social.InsufficientPermissionException;
import org.springframework.social.MissingAuthorizationException;
import org.springframework.social.ResourceNotFoundException;
import org.springframework.social.RevokedAuthorizationException;
import org.springframework.social.UncategorizedApiException;
import org.springframework.social.facebook.api.impl.FacebookTemplate;
import org.springframework.social.test.client.MockRestServiceServer;

import android.test.suitebuilder.annotation.MediumTest;

public class ErrorHandlingTest extends AbstractFacebookApiTest {

	@MediumTest
	public void testInsufficientPrivileges() {
		try {
			mockServer.expect(requestTo("https://graph.facebook.com/193482154020832/declined"))
				.andExpect(method(POST))
				.andExpect(header("Authorization", "OAuth someAccessToken"))
				.andRespond(withResponse(new ClassPathResource("testdata/error-insufficient-privilege.json", getClass()), responseHeaders, HttpStatus.FORBIDDEN, ""));
			facebook.eventOperations().declineInvitation("193482154020832");
			fail();
		} catch (InsufficientPermissionException e) {
			assertEquals("The operation requires 'rsvp_event' permission.", e.getMessage());
			assertEquals("rsvp_event", e.getRequiredPermission());
		}
	}
	
	@MediumTest
	public void testNotAFriend() {
		try {
			mockServer.expect(requestTo("https://graph.facebook.com/119297590579/members/100001387295207"))
				.andExpect(method(POST))
				.andExpect(header("Authorization", "OAuth someAccessToken"))
				.andRespond(withResponse(new ClassPathResource("testdata/error-not-a-friend.json", getClass()), responseHeaders, HttpStatus.INTERNAL_SERVER_ERROR, ""));
			facebook.friendOperations().addToFriendList("119297590579", "100001387295207");
			fail();
		} catch (NotAFriendException e) {
			assertEquals("The member must be a friend of the current user.", e.getMessage());
		}		
	}
	
	@MediumTest
	public void testUnknownPath() {
		try {
			mockServer.expect(requestTo("https://graph.facebook.com/me/boguspath"))
				.andExpect(method(GET))
				.andExpect(header("Authorization", "OAuth someAccessToken"))
				.andRespond(withResponse(new ClassPathResource("testdata/error-unknown-path.json", getClass()), responseHeaders, HttpStatus.BAD_REQUEST, ""));
			facebook.fetchConnections("me", "boguspath", String.class);
			fail();
		} catch (ResourceNotFoundException e) {
			assertEquals("Unknown path components: /boguspath", e.getMessage());
		}
	}
	
	@MediumTest
	public void testNotTheOwner() {
		try {
			mockServer.expect(requestTo("https://graph.facebook.com/1234567890"))
				.andExpect(method(POST))
				.andExpect(header("Authorization", "OAuth someAccessToken"))
				.andExpect(body("method=delete"))
				.andRespond(withResponse(new ClassPathResource("testdata/error-not-the-owner.json", getClass()), responseHeaders, HttpStatus.INTERNAL_SERVER_ERROR, ""));
			facebook.friendOperations().deleteFriendList("1234567890");
			fail();
		} catch (ResourceOwnershipException e) {
			assertEquals("User must be an owner of the friendlist", e.getMessage());
		}		
	}
	
	@MediumTest
	public void testUnknownAlias_HTTP200() {
		// yes, Facebook really does return this error as HTTP 200 (probably should be 404)
		try {
			mockServer.expect(requestTo("https://graph.facebook.com/dummyalias"))
				.andExpect(method(GET))
				.andExpect(header("Authorization", "OAuth someAccessToken"))
				.andRespond(withResponse(new ClassPathResource("testdata/error-unknown-alias.json", getClass()), responseHeaders, HttpStatus.OK, ""));
			facebook.fetchObject("dummyalias", FacebookProfile.class);
			fail("Expected GraphAPIException when fetching an unknown object alias");
		} catch (ResourceNotFoundException e) {
			assertEquals("(#803) Some of the aliases you requested do not exist: dummyalias", e.getMessage());
		}				
	}
	
	@MediumTest
	public void testCurrentUser_noAccessToken() {
		boolean success = false;
		try {
			FacebookTemplate facebook = new FacebookTemplate(); // use anonymous FacebookTemplate in this test
			MockRestServiceServer mockServer = MockRestServiceServer.createServer(facebook.getRestTemplate());
			mockServer.expect(requestTo("https://graph.facebook.com/me"))
				.andExpect(method(GET))
				.andRespond(withResponse(new ClassPathResource("testdata/error-current-user-no-token.json", getClass()), responseHeaders, HttpStatus.BAD_REQUEST, ""));
			facebook.userOperations().getUserProfile();
		} catch (MissingAuthorizationException e) {
			success = true;
		}					
		assertTrue("Expected MissingAuthorizationException", success);
	}
	
	@MediumTest
	public void testHtmlErrorResponse() {
		boolean success = false;
		try {
			FacebookTemplate facebook = new FacebookTemplate(); // use anonymous FacebookTemplate in this test
			MockRestServiceServer mockServer = MockRestServiceServer.createServer(facebook.getRestTemplate());
			mockServer.expect(requestTo("https://graph.facebook.com/123456/picture?type=normal"))
				.andExpect(method(GET))
				.andRespond(withResponse(new ClassPathResource("testdata/error-not-json.html", getClass()), responseHeaders, HttpStatus.BAD_REQUEST, ""));
			facebook.userOperations().getUserProfileImage("123456");
			fail("Expected UncategorizedApiException");
		} catch(UncategorizedApiException e) {
			success = true;
		}
		assertTrue("Expected UncategorizedApiException", success);
	}
	
	@MediumTest
	public void testTokenInvalid_tokenExpired() {
		boolean success = false;
		try {
			MockRestServiceServer mockServer = MockRestServiceServer.createServer(facebook.getRestTemplate());
			mockServer.expect(requestTo("https://graph.facebook.com/me"))
				.andExpect(method(GET))
				.andRespond(withResponse(new ClassPathResource("testdata/error-expired-token.json", getClass()), responseHeaders, HttpStatus.BAD_REQUEST, ""));
			facebook.userOperations().getUserProfile();
		} catch (ExpiredAuthorizationException e) {
			success = true;
		}
		assertTrue("Expected ExpiredAuthorizationException", success);
	}
	
	@MediumTest
	public void testTokenInvalid_passwordChanged() {
		boolean success = false;
		try {
			MockRestServiceServer mockServer = MockRestServiceServer.createServer(facebook.getRestTemplate());
			mockServer.expect(requestTo("https://graph.facebook.com/me"))
				.andExpect(method(GET))
				.andRespond(withResponse(new ClassPathResource("testdata/error-invalid-token-password.json", getClass()), responseHeaders, HttpStatus.BAD_REQUEST, ""));
			facebook.userOperations().getUserProfile();
		} catch (RevokedAuthorizationException e) {
			success = true;
		}
		assertTrue("Expected RevokedAuthorizationException", success);
	}
	
	@MediumTest
	public void testTokenInvalid_applicationDeauthorized() {
		boolean success = false;
		try {
			MockRestServiceServer mockServer = MockRestServiceServer.createServer(facebook.getRestTemplate());
			mockServer.expect(requestTo("https://graph.facebook.com/me"))
				.andExpect(method(GET))
				.andRespond(withResponse(new ClassPathResource("testdata/error-invalid-token-deauth.json", getClass()), responseHeaders, HttpStatus.BAD_REQUEST, ""));
			facebook.userOperations().getUserProfile();
		} catch (RevokedAuthorizationException e) {
			success = true;
		}
		assertTrue("Expected RevokedAuthorizationException", success);
	}

	@MediumTest
	public void testTokenInvalid_signedOutOfFacebook() {
		boolean success = false;
		try {
			MockRestServiceServer mockServer = MockRestServiceServer.createServer(facebook.getRestTemplate());
			mockServer.expect(requestTo("https://graph.facebook.com/me"))
				.andExpect(method(GET))
				.andRespond(withResponse(new ClassPathResource("testdata/error-invalid-token-signout.json", getClass()), responseHeaders, HttpStatus.BAD_REQUEST, ""));
			facebook.userOperations().getUserProfile();
		} catch (RevokedAuthorizationException e) {
			success = true;
		}
		assertTrue("Expected RevokedAuthorizationException", success);
	}
}