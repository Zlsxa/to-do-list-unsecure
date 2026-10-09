import hmac
import logging
import os

from django.http import HttpResponse, HttpResponseForbidden
from django.shortcuts import get_object_or_404, redirect, render
from django.views.decorators.http import require_http_methods, require_POST

from .forms import TaskForm
from .models import Task

logger = logging.getLogger(__name__)

LIST_URL = "/"


@require_http_methods(["GET", "POST"])
def index(request):
    form = TaskForm()
    if request.method == "POST":
        form = TaskForm(request.POST)
        if form.is_valid():
            task = form.save()
            logger.info("Tâche ajoutée : %s", task.pk)
            return redirect(LIST_URL)

    context = {"tasks": Task.objects.all(), "form": form}
    return render(request, "tasks/list.html", context)


@require_http_methods(["GET", "POST"])
def update_task(request, pk):
    task = get_object_or_404(Task, id=pk)
    form = TaskForm(instance=task)

    if request.method == "POST":
        form = TaskForm(request.POST, instance=task)
        if form.is_valid():
            form.save()
            logger.info("Tâche modifiée : %s", task.pk)
            return redirect(LIST_URL)

    return render(request, "tasks/update_task.html", {"form": form})


@require_http_methods(["GET", "POST"])
def delete_task(request, pk):
    item = get_object_or_404(Task, id=pk)

    if request.method == "POST":
        item.delete()
        logger.info("Tâche supprimée : %s", pk)
        return redirect(LIST_URL)

    return render(request, "tasks/delete.html", {"item": item})


@require_POST
def admin_panel(request):
    expected = os.environ.get("TODOLIST_ADMIN_PASSWORD", "")
    provided = request.POST.get("pwd", "")
    if expected and hmac.compare_digest(provided.encode(), expected.encode()):
        return HttpResponse("Bienvenue admin !")
    return HttpResponseForbidden("Accès refusé")
