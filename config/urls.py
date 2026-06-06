from django.urls import path, include

urlpatterns = [
    path('', include('finance.urls', namespace='finance')),
]
