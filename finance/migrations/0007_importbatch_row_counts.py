from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [('finance', '0006_installmentobligation')]
    operations = [
        migrations.AddField(model_name='importbatch', name='skipped_rows', field=models.PositiveIntegerField(default=0)),
        migrations.AddField(model_name='importbatch', name='total_rows', field=models.PositiveIntegerField(default=0)),
    ]
