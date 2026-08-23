from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):
    dependencies = [('finance', '0007_importbatch_row_counts')]

    operations = [
        migrations.CreateModel(
            name='TransactionSplit',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('amount', models.DecimalField(decimal_places=2, max_digits=10)),
                ('description', models.CharField(blank=True, max_length=255)),
                ('shared_with', models.CharField(blank=True, max_length=100)),
                ('is_reimbursable', models.BooleanField(default=False)),
                ('notes', models.TextField(blank=True)),
                ('budget_item', models.ForeignKey(on_delete=django.db.models.deletion.PROTECT, related_name='transaction_splits', to='finance.budgetitem')),
                ('transaction', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='splits', to='finance.transaction')),
            ],
            options={'db_table': 'finance_transaction_split', 'ordering': ['id']},
        ),
        migrations.AddConstraint(
            model_name='transactionsplit',
            constraint=models.CheckConstraint(check=models.Q(('amount__gt', 0)), name='finance_transaction_split_amount_gt_0'),
        ),
    ]