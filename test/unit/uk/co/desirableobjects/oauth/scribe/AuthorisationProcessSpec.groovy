package uk.co.desirableobjects.oauth.scribe

import spock.lang.Stepwise
import org.scribe.model.Token
import spock.lang.Shared
import org.scribe.oauth.OAuthService
import grails.plugin.spock.UnitSpec
import org.scribe.model.Verifier

import org.scribe.model.Verb
import org.scribe.model.Response
import org.springframework.http.HttpStatus
import spock.lang.Unroll

@Stepwise
@Mixin(GMockAddon)
class AuthorisationProcessSpec extends UnitSpec {

    private static final String DUMMY_OAUTH_RESOURCE_URI = 'http://example.org/list'
    @Shared Token requestToken
        @Shared Token accessToken
        @Shared OauthService oaService

        // TODO: This scenario: oauth_problem=signature_invalid&oauth_problem_advice=Failed%20to%20validate%20signature
        def 'a request token can be fetched'() {

                given:

                    mockConfig """
                        import org.scribe.builder.api.TwitterApi

                        oauth {
                            provider = TwitterApi
                            key = 'myKey'
                            secret = 'mySecret'
                        }
                    """
                    oaService = new OauthService()

                    oaService.service = mock(OAuthService)
                    oaService.service.getRequestToken().returns(new Token('a', 'b', 'c'))

                when:
                    simulate {
                        requestToken = oaService.requestToken
                    }

                then:
                    requestToken.rawResponse == 'c'

        }

        def 'make the user validate our token'() {

            given:

                oaService.service.getAuthorizationUrl(requestToken).returns('http://example.org/auth')

            expect:

                simulate {
                    oaService.getAuthorizationUrl(requestToken) == 'http://example.org/auth'
                }

        }

        def 'get the access token'() {

            given:

                Token expectedToken = new Token('d', 'e', 'f')
                Verifier verifier = new Verifier('abcde')
                oaService.service = mock(OAuthService)
                oaService.service.getAccessToken(requestToken, verifier).returns(expectedToken)

            when:

                simulate {
                    accessToken = oaService.getAccessToken(requestToken, verifier)
                }

           then:

                accessToken.rawResponse == 'f'

        }

        @Unroll({'make a $verb request using the authorised connection'})
        def 'make a request using the authorised connection'() {

            given:

                oaService.service = mock(OAuthService)

            and:
            
                String expectedResponse = 'Hello There.'
                Response oaResponse = mock(Response)
                oaResponse.getBody().returns(expectedResponse)
                oaResponse.getCode().returns(HttpStatus.OK.value())
            
            and:

                oaService.oauthResourceService = mock(OauthResourceService)
                oaService.oauthResourceService.accessResource(oaService.service, accessToken, verb, DUMMY_OAUTH_RESOURCE_URI, 30000, 30000).returns(oaResponse)

            when:

                String body = null
                int code = -1

            and:

                simulate {

                    def actualResponse = oaService."${verb.name().toLowerCase()}Resource"(accessToken, DUMMY_OAUTH_RESOURCE_URI)
                    body = actualResponse.body
                    code = actualResponse.code

                }

            then:

                code == HttpStatus.OK.value()
                body == expectedResponse

            where:

                verb << Verb.values()


        }

        def 'try to call an invalid resource accessor method on the service' () {

            when:

                oaService.failResource(accessToken, 'anyUrl')

            then:

                thrown(MissingMethodException)

        }

}
