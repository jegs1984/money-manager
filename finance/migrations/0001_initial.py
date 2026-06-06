from django.db import migrations, models
import django.db.models.deletion
import django.utils.timezone


class Migration(migrations.Migration):

    initial = True

    dependencies = [
        ('contenttypes', '0002_remove_content_type_name'),
    ]

    operations = [
        # ── Period ────────────────────────────────────────────────────────
        migrations.CreateModel(
            name='Period',
            fields=[
                ('id',         models.BigAutoField(primary_key=True, serialize=False)),
                ('name',       models.CharField(db_column='name',       max_length=100, unique=True)),
                ('start_date', models.DateField(db_column='start_date')),
                ('end_date',   models.DateField(db_column='end_date')),
                ('is_active',  models.BooleanField(db_column='is_active', default=True)),
            ],
            options={'db_table': 'finance_period', 'ordering': ['-start_date']},
        ),
        migrations.AddIndex(
            model_name='period',
            index=models.Index(fields=['start_date'], name='finance_period_start_date_idx'),
        ),
        migrations.AddIndex(
            model_name='period',
            index=models.Index(fields=['end_date'], name='finance_period_end_date_idx'),
        ),

        # ── Category ──────────────────────────────────────────────────────
        migrations.CreateModel(
            name='Category',
            fields=[
                ('id',    models.BigAutoField(primary_key=True, serialize=False)),
                ('name',  models.CharField(db_column='name',  max_length=100, unique=True)),
                # db_column quoted because "group" is a PostgreSQL reserved word
                ('group', models.CharField(db_column='"group"', max_length=100, choices=[
                    ('ESSENTIAL', 'Essential'),
                    ('LIFESTYLE', 'Lifestyle'),
                    ('SAVINGS',   'Savings'),
                    ('INCOME',    'Income'),
                ])),
            ],
            options={'db_table': 'finance_category', 'ordering': ['group', 'name'],
                     'verbose_name_plural': 'categories'},
        ),
        migrations.AddIndex(
            model_name='category',
            index=models.Index(fields=['name'], name='finance_category_name_idx'),
        ),

        # ── BudgetItem ────────────────────────────────────────────────────
        migrations.CreateModel(
            name='BudgetItem',
            fields=[
                ('id', models.BigAutoField(primary_key=True, serialize=False)),
                ('period', models.ForeignKey(
                    db_column='period_id',
                    on_delete=django.db.models.deletion.CASCADE,
                    related_name='budget_items',
                    to='finance.period',
                )),
                ('category', models.ForeignKey(
                    db_column='category_id',
                    on_delete=django.db.models.deletion.PROTECT,
                    related_name='budget_items',
                    to='finance.category',
                )),
                ('type',             models.CharField(db_column='type', max_length=3,
                                                      choices=[('IN', 'Income'), ('OUT', 'Expense')])),
                ('projected_amount', models.DecimalField(db_column='projected_amount',
                                                         decimal_places=2, default=0,
                                                         max_digits=10)),
            ],
            options={'db_table': 'finance_budget_item'},
        ),
        migrations.AddConstraint(
            model_name='budgetitem',
            constraint=models.CheckConstraint(
                check=models.Q(projected_amount__gte=0),
                name='finance_budget_item_projected_amount_gte_0',
            ),
        ),
        migrations.AddConstraint(
            model_name='budgetitem',
            constraint=models.UniqueConstraint(
                fields=['period', 'category'],
                name='finance_budget_item_period_category_unique',
            ),
        ),
        migrations.AddIndex(
            model_name='budgetitem',
            index=models.Index(fields=['period'],   name='finance_budget_item_period_id_idx'),
        ),
        migrations.AddIndex(
            model_name='budgetitem',
            index=models.Index(fields=['category'], name='finance_budget_item_category_id_idx'),
        ),

        # ── Transaction ───────────────────────────────────────────────────
        migrations.CreateModel(
            name='Transaction',
            fields=[
                ('id', models.BigAutoField(primary_key=True, serialize=False)),
                ('budget_item', models.ForeignKey(
                    db_column='budget_item_id',
                    null=True, blank=True,
                    on_delete=django.db.models.deletion.CASCADE,
                    related_name='transactions',
                    to='finance.budgetitem',
                )),
                ('date',        models.DateField(db_column='date')),
                ('real_amount', models.DecimalField(db_column='real_amount',
                                                    decimal_places=2, max_digits=10)),
                ('description', models.CharField(db_column='description', max_length=255)),
                ('notes',       models.TextField(db_column='notes', null=True, blank=True)),
            ],
            options={'db_table': 'finance_transaction', 'ordering': ['-date']},
        ),
        migrations.AddIndex(
            model_name='transaction',
            index=models.Index(fields=['date'],        name='finance_transaction_date_idx'),
        ),
        migrations.AddIndex(
            model_name='transaction',
            index=models.Index(fields=['budget_item'], name='finance_transaction_budget_item_id_idx'),
        ),

        # ── StagingTransaction ────────────────────────────────────────────
        migrations.CreateModel(
            name='StagingTransaction',
            fields=[
                ('id',             models.BigAutoField(primary_key=True, serialize=False)),
                ('source_file',    models.CharField(db_column='source_file',    max_length=255, null=True, blank=True)),
                ('account_number', models.CharField(db_column='account_number', max_length=50,  null=True, blank=True)),
                ('original_date',  models.DateField(db_column='original_date')),
                ('description',    models.CharField(db_column='description',    max_length=255)),
                ('doc_number',     models.CharField(db_column='doc_number',     max_length=20,  null=True, blank=True)),
                ('amount',         models.DecimalField(db_column='amount',      decimal_places=2, max_digits=10)),
                ('balance',        models.DecimalField(db_column='balance',     decimal_places=2, max_digits=14, null=True, blank=True)),
                ('type',           models.CharField(db_column='type',           max_length=3,
                                                    choices=[('IN', 'Income'), ('OUT', 'Expense')])),
                ('is_processed',   models.BooleanField(db_column='is_processed', default=False)),
                ('assigned_category', models.ForeignKey(
                    db_column='assigned_category_id',
                    null=True, blank=True,
                    on_delete=django.db.models.deletion.SET_NULL,
                    related_name='staging_transactions',
                    to='finance.category',
                )),
                ('created_at', models.DateTimeField(db_column='created_at', auto_now_add=True)),
            ],
            options={'db_table': 'finance_staging_transaction', 'ordering': ['-original_date', '-created_at']},
        ),
        migrations.AddIndex(
            model_name='stagingtransaction',
            index=models.Index(fields=['is_processed'],  name='finance_staging_transaction_is_processed_idx'),
        ),
        migrations.AddIndex(
            model_name='stagingtransaction',
            index=models.Index(fields=['original_date'], name='finance_staging_transaction_original_date_idx'),
        ),
        migrations.AddIndex(
            model_name='stagingtransaction',
            index=models.Index(fields=['assigned_category'], name='finance_staging_transaction_category_id_idx'),
        ),

        # ── StagingCCTransaction ──────────────────────────────────────────
        migrations.CreateModel(
            name='StagingCCTransaction',
            fields=[
                ('id',                  models.BigAutoField(primary_key=True, serialize=False)),
                ('source_file',         models.CharField(db_column='source_file',    max_length=255, null=True, blank=True)),
                ('card_number',         models.CharField(db_column='card_number',    max_length=30,  null=True, blank=True)),
                ('card_holder',         models.CharField(db_column='card_holder',    max_length=100, null=True, blank=True)),
                ('statement_date',      models.DateField(db_column='statement_date',               null=True, blank=True)),
                ('original_date',       models.DateField(db_column='original_date')),
                ('description',         models.CharField(db_column='description',    max_length=255)),
                ('location',            models.CharField(db_column='location',       max_length=100, null=True, blank=True)),
                ('ref_code',            models.CharField(db_column='ref_code',       max_length=30,  null=True, blank=True)),
                ('amount',              models.DecimalField(db_column='amount',      decimal_places=2, max_digits=10)),
                ('installment_current', models.SmallIntegerField(db_column='installment_current',   null=True, blank=True)),
                ('installment_total',   models.SmallIntegerField(db_column='installment_total',     null=True, blank=True)),
                ('installment_value',   models.DecimalField(db_column='installment_value', decimal_places=2, max_digits=10, null=True, blank=True)),
                ('type',                models.CharField(db_column='type', max_length=3,
                                                         choices=[('IN', 'Payment / Credit'), ('OUT', 'Purchase / Charge')])),
                ('is_processed',        models.BooleanField(db_column='is_processed', default=False)),
                ('assigned_category',   models.ForeignKey(
                    db_column='assigned_category_id',
                    null=True, blank=True,
                    on_delete=django.db.models.deletion.SET_NULL,
                    related_name='staging_cc_transactions',
                    to='finance.category',
                )),
                ('created_at', models.DateTimeField(db_column='created_at', auto_now_add=True)),
            ],
            options={'db_table': 'finance_staging_cc_transaction', 'ordering': ['-original_date', '-created_at']},
        ),
        migrations.AddIndex(
            model_name='stagingcctransaction',
            index=models.Index(fields=['is_processed'],  name='finance_staging_cc_is_processed_idx'),
        ),
        migrations.AddIndex(
            model_name='stagingcctransaction',
            index=models.Index(fields=['original_date'], name='finance_staging_cc_original_date_idx'),
        ),
        migrations.AddIndex(
            model_name='stagingcctransaction',
            index=models.Index(fields=['assigned_category'], name='finance_staging_cc_category_id_idx'),
        ),
    ]
