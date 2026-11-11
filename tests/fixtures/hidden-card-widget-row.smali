    invoke-virtual {v1}, LQ3/T3;->m()Z

    move-result v2

    move-object/from16 v4, p0

    iget-object v5, v4, LQ3/T5;->s:Ljava/util/List;

    iget-object v6, v4, LQ3/T5;->R:LN/R0;

    if-eqz v2, :cond_1f

    if-eqz v39, :cond_1e

    goto :goto_f

    :cond_1e
    move-object/from16 v23, v5

    move-object/from16 v40, v6

    move-wide/from16 v49, v18

    move-object/from16 v2, v24

    move-object/from16 v48, v41

    move-object/from16 v51, v42

    move-object/from16 v52, v43

    const/4 v0, 0x0

    move-object/from16 v42, v1

    move-object v1, v14

    goto/16 :goto_17

    :cond_1f
    :goto_f
    invoke-virtual {v1}, LQ3/T3;->j()Z

    move-result v2

    if-eqz v2, :cond_2e

    if-nez v39, :cond_2e

