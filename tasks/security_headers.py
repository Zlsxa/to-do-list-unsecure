class SecurityHeadersMiddleware:
    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        response = self.get_response(request)
        response.setdefault(
            "Content-Security-Policy",
            "default-src 'self'; frame-ancestors 'none'; object-src 'none'",
        )
        response.setdefault("Referrer-Policy", "same-origin")
        response.setdefault("Permissions-Policy", "geolocation=(), microphone=(), camera=()")
        response.setdefault("Cross-Origin-Embedder-Policy", "require-corp")
        response.setdefault("Cross-Origin-Resource-Policy", "same-origin")
        response.setdefault("Cross-Origin-Opener-Policy", "same-origin")
        response.setdefault("X-Content-Type-Options", "nosniff")
        response.setdefault("Cache-Control", "no-store")
        return response
