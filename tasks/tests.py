from django.test import TestCase
from django.urls import reverse

from tasks.models import Task
from tasks.forms import TaskForm


class TaskModelTest(TestCase):
    """Tests liés au modèle Task"""

    def test_task_creation_defaults(self):
        task = Task.objects.create(title="Test task")

        self.assertEqual(task.title, "Test task")
        self.assertFalse(task.complete)
        self.assertIsNotNone(task.created)

    def test_task_str_representation(self):
        task = Task.objects.create(title="Ma tâche")
        self.assertEqual(str(task), "Ma tâche")


class TaskFormTest(TestCase):
    """Tests du formulaire TaskForm"""

    def test_task_form_valid(self):
        form = TaskForm(data={
            "title": "Nouvelle tâche",
            "complete": False
        })
        self.assertTrue(form.is_valid())

    def test_task_form_invalid_without_title(self):
        form = TaskForm(data={
            "complete": False
        })
        self.assertFalse(form.is_valid())
        self.assertIn("title", form.errors)


class TaskUrlsTest(TestCase):
    """Tests de résolution des URLs"""

    def test_index_url_accessible(self):
        response = self.client.get(reverse("list"))
        self.assertEqual(response.status_code, 200)


class TaskViewsTest(TestCase):
    """Tests des vues"""

    def setUp(self):
        self.task = Task.objects.create(title="Task initiale")

    def test_index_view_lists_tasks(self):
        response = self.client.get("/")
        self.assertEqual(response.status_code, 200)
        self.assertContains(response, "Task initiale")

    def test_create_task_via_post(self):
        response = self.client.post("/", {
            "title": "Task POST",
            "complete": False
        })

        self.assertEqual(Task.objects.count(), 2)
        self.assertRedirects(response, "/")

    def test_update_task_get(self):
        response = self.client.get(f"/update_task/{self.task.id}/")
        self.assertEqual(response.status_code, 200)
        self.assertContains(response, "Task initiale")

    def test_update_task_post(self):
        response = self.client.post(
            f"/update_task/{self.task.id}/",
            {
                "title": "Task modifiée",
                "complete": True
            }
        )

        self.task.refresh_from_db()
        self.assertEqual(self.task.title, "Task modifiée")
        self.assertTrue(self.task.complete)
        self.assertRedirects(response, "/")

    def test_delete_task_get(self):
        response = self.client.get(f"/delete_task/{self.task.id}/")
        self.assertEqual(response.status_code, 200)
        self.assertContains(response, "Task initiale")

    def test_delete_task_post(self):
        response = self.client.post(f"/delete_task/{self.task.id}/")

        self.assertEqual(Task.objects.count(), 0)
        self.assertRedirects(response, "/")


class TaskErrorHandlingTest(TestCase):
    """Cas d'erreur et sécurité des vues"""

    def test_update_unknown_task_returns_404(self):
        response = self.client.get("/update_task/9999/")
        self.assertEqual(response.status_code, 404)

    def test_delete_unknown_task_returns_404(self):
        response = self.client.post("/delete_task/9999/")
        self.assertEqual(response.status_code, 404)

    def test_create_task_invalid_form_stays_on_page(self):
        response = self.client.post("/", {"title": ""})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(Task.objects.count(), 0)

    def test_title_is_html_escaped(self):
        Task.objects.create(title="<script>alert(1)</script>")
        response = self.client.get("/")
        self.assertNotContains(response, "<script>alert(1)</script>")
        self.assertContains(response, "&lt;script&gt;")

    def test_method_not_allowed(self):
        response = self.client.put("/")
        self.assertEqual(response.status_code, 405)


class AdminPanelTest(TestCase):
    """Le mot de passe admin vient de l'environnement, jamais du code"""

    def test_admin_panel_refuses_without_configured_password(self):
        with self.settings():
            import os
            os.environ.pop("TODOLIST_ADMIN_PASSWORD", None)
            response = self.client.post("/admin_panel/", {"pwd": "nimporte"})
        self.assertEqual(response.status_code, 403)

    def test_admin_panel_accepts_env_password(self):
        import os
        os.environ["TODOLIST_ADMIN_PASSWORD"] = "mot-de-passe-de-test"
        try:
            ok = self.client.post("/admin_panel/", {"pwd": "mot-de-passe-de-test"})
            ko = self.client.post("/admin_panel/", {"pwd": "faux"})
        finally:
            os.environ.pop("TODOLIST_ADMIN_PASSWORD", None)
        self.assertEqual(ok.status_code, 200)
        self.assertEqual(ko.status_code, 403)

    def test_admin_panel_get_not_allowed(self):
        response = self.client.get("/admin_panel/")
        self.assertEqual(response.status_code, 405)
