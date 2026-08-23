from django.conf import settings
from django.shortcuts import redirect


class LocalDeploymentLoginRequiredMiddleware:
    """Keep a deliberately local app private if it is exposed by mistake."""

    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        if (
            settings.REQUIRE_LOGIN
            and not request.user.is_authenticated
            and not request.path.startswith((settings.LOGIN_URL, settings.STATIC_URL))
        ):
            return redirect(f'{settings.LOGIN_URL}?next={request.get_full_path()}')
        return self.get_response(request)
