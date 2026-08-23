from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):
    dependencies = [('finance', '0004_account_period_closed_at_transaction_reversal_of_and_more')]

    operations = [
        migrations.CreateModel(
            name='Goal',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('name', models.CharField(max_length=100, unique=True)),
                ('target_amount', models.DecimalField(decimal_places=2, max_digits=14)),
                ('saved_amount', models.DecimalField(decimal_places=2, default=0, max_digits=14)),
                ('target_date', models.DateField(blank=True, null=True)),
                ('notes', models.TextField(blank=True)), ('is_complete', models.BooleanField(default=False)),
            ],
            options={'db_table': 'finance_goal', 'ordering': ['is_complete', 'target_date', 'name']},
        ),
        migrations.CreateModel(
            name='RecurringPlan',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('name', models.CharField(max_length=100, unique=True)),
                ('transaction_type', models.CharField(choices=[('IN', 'Income'), ('OUT', 'Expense')], max_length=3)),
                ('amount', models.DecimalField(decimal_places=2, max_digits=14)),
                ('frequency', models.CharField(choices=[('MONTHLY', 'Monthly'), ('WEEKLY', 'Weekly')], default='MONTHLY', max_length=10)),
                ('next_date', models.DateField()), ('description', models.CharField(max_length=255)), ('is_active', models.BooleanField(default=True)),
                ('account', models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.SET_NULL, related_name='recurring_plans', to='finance.account')),
                ('category', models.ForeignKey(on_delete=django.db.models.deletion.PROTECT, related_name='recurring_plans', to='finance.category')),
            ],
            options={'db_table': 'finance_recurring_plan', 'ordering': ['next_date', 'name']},
        ),
        migrations.CreateModel(
            name='MerchantRule',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('description_pattern', models.CharField(max_length=120, unique=True)),
                ('transaction_type', models.CharField(blank=True, choices=[('IN', 'Income'), ('OUT', 'Expense')], max_length=3)),
                ('is_active', models.BooleanField(default=True)), ('created_at', models.DateTimeField(auto_now_add=True)),
                ('category', models.ForeignKey(on_delete=django.db.models.deletion.PROTECT, related_name='merchant_rules', to='finance.category')),
            ],
            options={'db_table': 'finance_merchant_rule', 'ordering': ['description_pattern']},
        ),
        migrations.AddConstraint(model_name='goal', constraint=models.CheckConstraint(check=models.Q(target_amount__gt=0), name='finance_goal_target_amount_gt_0')),
        migrations.AddConstraint(model_name='goal', constraint=models.CheckConstraint(check=models.Q(saved_amount__gte=0), name='finance_goal_saved_amount_gte_0')),
    ]
