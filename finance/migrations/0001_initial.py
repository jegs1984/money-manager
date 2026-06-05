# Generated migration for initial schema

from django.db import migrations, models
import django.db.models.deletion
import django.core.validators


class Migration(migrations.Migration):

    initial = True

    dependencies = [
    ]

    operations = [
        migrations.CreateModel(
            name='Group',
            fields=[
                ('id', models.BigAutoField(primary_key=True, serialize=False)),
                ('name', models.CharField(db_index=True, max_length=100, unique=True)),
            ],
            options={
                'db_table': 'finance_group',
                'ordering': ['name'],
            },
        ),
        migrations.CreateModel(
            name='BudgetMonth',
            fields=[
                ('id', models.BigAutoField(primary_key=True, serialize=False)),
                ('month_date', models.DateField(db_index=True, unique=True)),
                ('is_active', models.BooleanField(default=True)),
            ],
            options={
                'db_table': 'finance_budget_month',
                'ordering': ['-month_date'],
            },
        ),
        migrations.CreateModel(
            name='Category',
            fields=[
                ('id', models.BigAutoField(primary_key=True, serialize=False)),
                ('name', models.CharField(db_index=True, max_length=100, unique=True)),
                ('group', models.ForeignKey(db_column='group_id', on_delete=django.db.models.deletion.PROTECT, related_name='categories', to='finance.group')),
            ],
            options={
                'db_table': 'finance_category',
                'ordering': ['group', 'name'],
            },
        ),
        migrations.CreateModel(
            name='BudgetItem',
            fields=[
                ('id', models.BigAutoField(primary_key=True, serialize=False)),
                ('type', models.CharField(db_column='type', max_length=3, choices=[('IN', 'Income'), ('OUT', 'Expense')])),
                ('projected_amount', models.DecimalField(decimal_places=2, default='0.00', max_digits=10, validators=[django.core.validators.MinValueValidator(0)])),
                ('budget_month', models.ForeignKey(db_column='budget_month_id', on_delete=django.db.models.deletion.CASCADE, related_name='budget_items', to='finance.budgetmonth')),
                ('category', models.ForeignKey(db_column='category_id', on_delete=django.db.models.deletion.PROTECT, related_name='budget_items', to='finance.category')),
            ],
            options={
                'db_table': 'finance_budget_item',
            },
        ),
        migrations.AddConstraint(
            model_name='budgetitem',
            constraint=models.CheckConstraint(check=models.Q(('projected_amount__gte', 0)), name='budget_item_projected_amount_non_negative'),
        ),
        migrations.AddConstraint(
            model_name='budgetitem',
            constraint=models.UniqueConstraint(fields=['budget_month', 'category'], name='budget_item_budget_month_category_unique'),
        ),
        migrations.AddIndex(
            model_name='budgetitem',
            index=models.Index(fields=['budget_month'], name='finance_bud_budget__96c46d_idx'),
        ),
        migrations.AddIndex(
            model_name='budgetitem',
            index=models.Index(fields=['category'], name='finance_bud_categor_ac1ed5_idx'),
        ),
        migrations.CreateModel(
            name='Transaction',
            fields=[
                ('id', models.BigAutoField(primary_key=True, serialize=False)),
                ('date', models.DateField(db_index=True)),
                ('real_amount', models.DecimalField(decimal_places=2, max_digits=10)),
                ('description', models.CharField(max_length=255)),
                ('notes', models.TextField(blank=True, null=True)),
                ('budget_item', models.ForeignKey(blank=True, db_column='budget_item_id', null=True, on_delete=django.db.models.deletion.CASCADE, related_name='transactions', to='finance.budgetitem')),
            ],
            options={
                'db_table': 'finance_transaction',
                'ordering': ['-date'],
            },
        ),
        migrations.AddIndex(
            model_name='transaction',
            index=models.Index(fields=['date'], name='finance_tra_date_f21d66_idx'),
        ),
        migrations.AddIndex(
            model_name='transaction',
            index=models.Index(fields=['budget_item'], name='finance_tra_budget__392700_idx'),
        ),
        migrations.CreateModel(
            name='StagingTransaction',
            fields=[
                ('id', models.BigAutoField(primary_key=True, serialize=False)),
                ('original_date', models.DateField()),
                ('description', models.CharField(max_length=255)),
                ('amount', models.DecimalField(decimal_places=2, max_digits=10)),
                ('type', models.CharField(choices=[('IN', 'Income'), ('OUT', 'Expense')], max_length=3)),
                ('is_processed', models.BooleanField(db_index=True, default=False)),
                ('assigned_category', models.ForeignKey(blank=True, db_column='assigned_category_id', null=True, on_delete=django.db.models.deletion.SET_NULL, related_name='staging_transactions', to='finance.category')),
            ],
            options={
                'db_table': 'finance_staging_transaction',
                'ordering': ['-original_date'],
            },
        ),
        migrations.AddIndex(
            model_name='stagingtransaction',
            index=models.Index(fields=['is_processed'], name='finance_sta_is_proc_594c99_idx'),
        ),
        migrations.AddIndex(
            model_name='stagingtransaction',
            index=models.Index(fields=['assigned_category'], name='finance_sta_assigne_c5412f_idx'),
        ),
    ]
